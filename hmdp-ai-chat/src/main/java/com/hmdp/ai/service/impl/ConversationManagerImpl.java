package com.hmdp.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.domain.ChatMessage;
import com.hmdp.ai.domain.UserContext;
import com.hmdp.ai.domain.entity.ChatMessageEntity;
import com.hmdp.ai.domain.entity.ChatSession;
import com.hmdp.ai.mapper.ChatMessageMapper;
import com.hmdp.ai.mapper.ChatSessionMapper;
import com.hmdp.ai.service.ConversationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationManagerImpl implements ConversationManager {

    private static final String KEY_SESSION = "chat:session:";
    private static final String KEY_HISTORY = "chat:history:";
    private static final String KEY_USER_CONTEXT = "chat:user:context:";
    private static final String KEY_ACTIVE_SESSION = "chat:user:active_session:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Override
    public String createSession(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        long ttlHours = aiProperties.getConversation().getSessionTtlHours();

        // 存储会话元数据到 Redis Hash
        String sessionKey = KEY_SESSION + sessionId;
        redisTemplate.opsForHash().put(sessionKey, "userId", String.valueOf(userId));
        redisTemplate.opsForHash().put(sessionKey, "status", "1");
        redisTemplate.opsForHash().put(sessionKey, "createTime", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().put(sessionKey, "messageCount", "0");
        redisTemplate.expire(sessionKey, ttlHours, TimeUnit.HOURS);

        // 更新用户活跃会话
        String activeKey = KEY_ACTIVE_SESSION + userId;
        redisTemplate.opsForValue().set(activeKey, sessionId, ttlHours, TimeUnit.HOURS);

        // 持久化到 MySQL
        ChatSession session = ChatSession.builder()
                .id(sessionId)
                .userId(userId)
                .status(1)
                .messageCount(0)
                .createTime(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(session);

        log.info("Created session {} for user {}", sessionId, userId);
        return sessionId;
    }

    @Override
    public List<ChatMessage> getHistory(String sessionId, int maxRounds) {
        int rounds = maxRounds > 0 ? maxRounds : aiProperties.getConversation().getMaxHistoryRounds();
        String historyKey = KEY_HISTORY + sessionId;

        // 从 Redis 获取最近 N 条消息
        List<String> jsonList = redisTemplate.opsForList().range(historyKey, -rounds, -1);

        if (jsonList != null && !jsonList.isEmpty()) {
            return jsonList.stream()
                    .map(this::deserializeMessage)
                    .filter(msg -> msg != null)
                    .collect(Collectors.toList());
        }

        // Redis 中不存在，从 MySQL 查询归档消息
        log.debug("History not found in Redis for session {}, querying MySQL", sessionId);
        List<ChatMessageEntity> entities = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ChatMessageEntity::getCreateTime)
                        .last("LIMIT " + rounds)
        );

        return entities.stream()
                .map(e -> ChatMessage.builder()
                        .role(e.getRole())
                        .content(e.getContent())
                        .messageType(e.getMessageType())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        if (message.getCreateTime() == null) {
            message.setCreateTime(System.currentTimeMillis());
        }

        String historyKey = KEY_HISTORY + sessionId;
        String sessionKey = KEY_SESSION + sessionId;
        long ttlHours = aiProperties.getConversation().getSessionTtlHours();

        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(historyKey, json);
            redisTemplate.expire(historyKey, ttlHours, TimeUnit.HOURS);

            // 更新消息计数
            redisTemplate.opsForHash().increment(sessionKey, "messageCount", 1);
            redisTemplate.expire(sessionKey, ttlHours, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for session {}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public void endSession(String sessionId) {
        String historyKey = KEY_HISTORY + sessionId;
        String sessionKey = KEY_SESSION + sessionId;

        // 获取 userId
        String userIdStr = (String) redisTemplate.opsForHash().get(sessionKey, "userId");
        Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;

        // 从 Redis 获取全部消息并归档到 MySQL
        List<String> allMessages = redisTemplate.opsForList().range(historyKey, 0, -1);
        if (allMessages != null && !allMessages.isEmpty()) {
            List<ChatMessageEntity> entities = allMessages.stream()
                    .map(json -> {
                        ChatMessage msg = deserializeMessage(json);
                        if (msg == null) return null;
                        return ChatMessageEntity.builder()
                                .sessionId(sessionId)
                                .role(msg.getRole())
                                .content(msg.getContent())
                                .messageType(msg.getMessageType() != null ? msg.getMessageType() : "text")
                                .createTime(msg.getCreateTime() != null
                                        ? java.time.Instant.ofEpochMilli(msg.getCreateTime())
                                                .atZone(java.time.ZoneId.systemDefault())
                                                .toLocalDateTime()
                                        : LocalDateTime.now())
                                .build();
                    })
                    .filter(e -> e != null)
                    .collect(Collectors.toList());

            // 批量插入（MyBatis-Plus 逐条插入）
            entities.forEach(chatMessageMapper::insert);
        }

        // 更新 MySQL 会话状态
        chatSessionMapper.update(null,
                new LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .set(ChatSession::getStatus, 2)
                        .set(ChatSession::getEndTime, LocalDateTime.now()));

        // 清理 Redis
        redisTemplate.delete(historyKey);
        redisTemplate.delete(sessionKey);
        if (userId != null) {
            redisTemplate.delete(KEY_ACTIVE_SESSION + userId);
        }

        log.info("Session {} ended and archived", sessionId);
    }

    @Override
    public UserContext getUserContext(Long userId, String sessionId) {
        String contextKey = KEY_USER_CONTEXT + userId;

        // 尝试从 Redis 缓存读取
        String cachedJson = (String) redisTemplate.opsForHash().get(contextKey, "data");
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, UserContext.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize user context for user {}", userId);
            }
        }

        // 构建基础上下文
        UserContext context = UserContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .recentOrderTokens(new ArrayList<>())
                .recentVoucherIds(new ArrayList<>())
                .lastInteractTime(System.currentTimeMillis())
                .build();

        // 缓存到 Redis（TTL 1h）
        try {
            redisTemplate.opsForHash().put(contextKey, "data", objectMapper.writeValueAsString(context));
            redisTemplate.expire(contextKey, 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache user context for user {}", userId);
        }

        return context;
    }

    @Override
    public String getActiveSessionId(Long userId) {
        return redisTemplate.opsForValue().get(KEY_ACTIVE_SESSION + userId);
    }

    private ChatMessage deserializeMessage(String json) {
        try {
            return objectMapper.readValue(json, ChatMessage.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize message: {}", e.getMessage());
            return null;
        }
    }
}
