package com.hmdp.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.domain.ChatMessage;
import com.hmdp.ai.domain.DocumentChunk;
import com.hmdp.ai.domain.UserContext;
import com.hmdp.ai.domain.dto.*;
import com.hmdp.ai.domain.entity.ChatFeedbackEntity;
import com.hmdp.ai.domain.vo.SseEventVO;
import com.hmdp.ai.engine.*;
import com.hmdp.ai.engine.impl.OpenAILLMEngine;
import com.hmdp.ai.mapper.ChatFeedbackMapper;
import com.hmdp.ai.service.ContentFilter;
import com.hmdp.ai.service.ConversationManager;
import com.hmdp.ai.service.RateLimiter;
import com.hmdp.ai.service.TokenCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 聊天控制器
 * 提供消息发送、SSE 流式推送、用户反馈等端点
 * 串联完整主流程：意图识别 → RAG 检索 → LLM 调用（含 Function Calling）→ 质量校验 → SSE 推送
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final LLMEngine llmEngine;
    private final ObjectMapper objectMapper;
    private final ContentFilter contentFilter;
    private final ConversationManager conversationManager;
    private final IntentResolver intentResolver;
    private final RAGModule ragModule;
    private final FunctionCallingEngine functionCallingEngine;
    private final QualityGuard qualityGuard;
    private final AiProperties aiProperties;
    private final ChatFeedbackMapper chatFeedbackMapper;
    private final TokenCounter tokenCounter;
    private final StringRedisTemplate redisTemplate;
    private final RateLimiter rateLimiter;

    /** 存储待推送的 SSE emitter（sessionId -> emitter） */
    private final ConcurrentHashMap<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /** 存储待处理的消息（sessionId -> [messageId, dto]），等 SSE 连接建立后触发 */
    private final ConcurrentHashMap<String, Object[]> pendingMessages = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ---- 端点 ----

    /**
     * POST /api/chat/send
     * 接收用户消息，触发完整处理流程，返回 sessionId 和 messageId
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> send(@RequestBody SendMessageDTO dto) {
        // 从 authToken 解析真实 userId（token 存在 Redis 的 login:token:{token} -> userId）
        Long userId = dto.getUserId() != null && dto.getUserId() > 0 ? dto.getUserId() : 0L;
        if (dto.getAuthToken() != null && !dto.getAuthToken().isBlank()) {
            try {
                String userIdStr = redisTemplate.opsForValue().get("login:token:" + dto.getAuthToken());
                if (userIdStr != null) {
                    userId = Long.parseLong(userIdStr);
                    dto.setUserId(userId);
                }
            } catch (Exception e) {
                log.warn("Failed to resolve userId from token: {}", e.getMessage());
            }
        }

        String sessionId = dto.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = conversationManager.createSession(userId);
        }
        String messageId = UUID.randomUUID().toString();

        // 敏感内容过滤
        if (contentFilter.isViolation(dto.getContent())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CONTENT_VIOLATION",
                    "message", ContentFilter.VIOLATION_REPLY
            ));
        }

        // 用户级限流：滑动窗口内请求次数检查
        if (!rateLimiter.allowUser(userId)) {
            AiProperties.RateLimit cfg = aiProperties.getRateLimit();
            log.warn("User rate limit rejected: userId={}", userId);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RATE_LIMIT_EXCEEDED",
                    "message", String.format("您的提问太频繁了，每%d秒最多提问%d次，请稍后再试。",
                            cfg.getWindowSeconds(), cfg.getMaxRequestsPerWindow())
            ));
        }

        // 全局并发限流：LLM 同时处理请求数检查
        if (!rateLimiter.tryAcquireGlobal()) {
            log.warn("Global concurrency limit rejected: userId={}, concurrency={}",
                    userId, rateLimiter.currentConcurrency());
            return ResponseEntity.status(429).body(Map.of(
                    "error", "SERVER_BUSY",
                    "message", "当前咨询人数较多，请稍等片刻再试。"
            ));
        }

        // 追加用户消息到会话历史
        conversationManager.appendMessage(sessionId, ChatMessage.builder()
                .role("user").content(dto.getContent()).build());

        // 将待处理的消息存入 pendingMessages，等 SSE 连接建立后再处理
        pendingMessages.put(sessionId, new Object[]{messageId, dto});
        log.info("Message queued for session: {}", sessionId);

        return ResponseEntity.ok(Map.of("sessionId", sessionId, "messageId", messageId));
    }

    /**
     * POST /api/chat/session
     * 创建新会话
     */
    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession(@RequestBody Map<String, Long> body) {
        Long userId = body != null ? body.get("userId") : null;
        if (userId == null) return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        return ResponseEntity.ok(Map.of("sessionId", conversationManager.createSession(userId)));
    }

    /**
     * DELETE /api/chat/session/{sessionId}
     * 结束会话并归档
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, String>> endSession(@PathVariable String sessionId) {
        conversationManager.endSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "ended", "sessionId", sessionId));
    }

    /**
     * GET /api/chat/history/{sessionId}
     * 获取会话历史消息，支持分页（page、size 参数）
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<Map<String, Object>> getHistory(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int maxRounds = page * size;
        List<ChatMessage> allHistory = conversationManager.getHistory(sessionId, maxRounds);
        int total = allHistory.size();
        int fromIndex = Math.max(0, total - page * size);
        int toIndex = Math.max(0, total - (page - 1) * size);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId, "page", page, "size", size,
                "total", total, "messages", allHistory.subList(fromIndex, toIndex)
        ));
    }

    /**
     * GET /api/chat/stream/{sessionId}
     * SSE 流式端点，前端订阅后接收 LLM 回复 token
     * 连接建立后立即触发待处理消息的处理流程
     */
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId,
                             @RequestParam(required = false) String messageId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        emitterMap.put(sessionId, emitter);
        emitter.onCompletion(() -> emitterMap.remove(sessionId));
        emitter.onTimeout(() -> {
            emitterMap.remove(sessionId);
            log.warn("SSE timeout: {}", sessionId);
        });
        emitter.onError(e -> {
            emitterMap.remove(sessionId);
            log.error("SSE error: {}", sessionId, e);
        });

        // SSE 连接建立后，立即触发待处理消息
        Object[] pending = pendingMessages.remove(sessionId);
        if (pending != null) {
            String pendingMessageId = (String) pending[0];
            SendMessageDTO pendingDto = (SendMessageDTO) pending[1];
            log.info("SSE connected, triggering processing for session: {}", sessionId);
            // 短暂延迟确保 SSE 连接完全就绪后再开始推送
            executor.submit(() -> {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                processAndStream(sessionId, pendingMessageId, pendingDto);
            });
        }

        return emitter;
    }

    /**
     * POST /api/chat/feedback
     * 接收用户反馈，持久化到 chat_feedback 表
     * 如果是 wrong_entity 类型，触发重新回复
     */
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, String>> feedback(@RequestBody FeedbackDTO dto) {
        try {
            // 持久化反馈
            ChatFeedbackEntity entity = ChatFeedbackEntity.builder()
                    .sessionId(dto.getSessionId())
                    .messageId(dto.getMessageId())
                    .userId(dto.getUserId() != null ? dto.getUserId() : 0L)
                    .feedbackType(dto.getFeedbackType())
                    .detail(dto.getDetail())
                    .resolved(0)
                    .createTime(LocalDateTime.now())
                    .build();
            chatFeedbackMapper.insert(entity);

            log.info("Feedback saved: session={}, type={}", dto.getSessionId(), dto.getFeedbackType());
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Failed to save feedback: {}", e.getMessage());
            // 反馈失败不影响用户体验
            return ResponseEntity.ok(Map.of("status", "received"));
        }
    }

    // ---- 核心处理流程 ----

    /**
     * 完整的消息处理与流式推送流程：
     * 加载上下文 → 意图识别 → RAG 检索 → 构建 Prompt → LLM 调用（含 Function Calling）
     * → 质量校验 → 推送 done → 保存历史 → 记录监控
     */
    private void processAndStream(String sessionId, String messageId, SendMessageDTO dto) {
        long startTime = System.currentTimeMillis();
        // emitter 在 stream 接口建立连接时已经放入 emitterMap，直接取用
        SseEmitter emitter = emitterMap.get(sessionId);
        if (emitter == null) {
            log.warn("No SSE emitter for session: {}", sessionId);
            rateLimiter.releaseGlobal(dto.getUserId());
            return;
        }

        Long userId = dto.getUserId() != null ? dto.getUserId() : 0L;
        try {

            log.info("┌─────────────────────────────────────────────────────");
            log.info("│ [CHAT] session={} userId={} msg=\"{}\"",
                    sessionId, userId, dto.getContent().length() > 50
                            ? dto.getContent().substring(0, 50) + "..." : dto.getContent());

            // Step 1: 加载对话上下文
            List<ChatMessage> history = conversationManager.getHistory(sessionId,
                    aiProperties.getConversation().getMaxHistoryRounds());
            UserContext userContext = conversationManager.getUserContext(userId, sessionId);
            if (dto.getPageContext() != null) {
                userContext.setPageContext(dto.getPageContext());
            }
            // 把登录 token 存入上下文，供 Function Calling 调用秒杀系统时鉴权
            if (dto.getAuthToken() != null && !dto.getAuthToken().isBlank()) {
                userContext.setAuthToken(dto.getAuthToken());
            }
            log.info("│ [Step1] 历史消息={}轮, hasToken={}", history.size() / 2,
                    dto.getAuthToken() != null && !dto.getAuthToken().isBlank());

            // Step 2: 意图识别
            ResolveContext resolveContext = ResolveContext.builder()
                    .history(history)
                    .userContext(userContext)
                    .pageContext(dto.getPageContext())
                    .build();
            IntentResult intentResult = intentResolver.resolve(dto.getContent(), resolveContext);
            log.info("│ [Step2] 意图识别: intent={} confidence={} needsConfirm={} reasoning={}",
                    intentResult.getIntent(),
                    String.format("%.2f", intentResult.getConfidence()),
                    intentResult.isNeedsConfirmation(),
                    intentResult.getReasoning());

            // 如果需要用户确认，且不是 unknown 意图，直接推送澄清问题，不调用 LLM
            // unknown 意图直接走 LLM 回复（LLM 会礼貌拒绝无关问题）
            if (intentResult.isNeedsConfirmation() && !"unknown".equals(intentResult.getIntent())) {
                String clarification = intentResult.getClarificationQuestion();
                log.info("│ [Step2] 触发澄清流程，推送确认问题: {}", clarification);
                log.info("└─────────────────────────────────────────────────────");
                sendSseEvent(emitter, "token", SseEventVO.builder()
                        .type(SseEventVO.EventType.TOKEN).content(clarification).build());
                conversationManager.appendMessage(sessionId, ChatMessage.builder()
                        .role("assistant").content(clarification).build());
                sendSseEvent(emitter, "done", SseEventVO.builder()
                        .type(SseEventVO.EventType.DONE).messageId(messageId).build());
                emitter.complete();
                rateLimiter.releaseGlobal(userId);
                return;
            }

            // Step 3: RAG 检索
            List<DocumentChunk> ragChunks = new ArrayList<>();
            String intent = intentResult.getIntent();
            // 是否带工具：knowledge_qa 意图不带工具（避免 LLM 误触发），其他意图都带
            boolean withTools = !isKnowledgeOnlyIntent(intent);

            // 是否需要 RAG：带工具的意图直接调接口获取实时数据，不需要 RAG
            // 只有不带工具的意图（knowledge_qa / unknown）才需要 RAG 提供知识库背景
            boolean skipRag = withTools;

            if (skipRag) {
                log.info("│ [Step3] RAG跳过 → 带工具意图，将由LLM决策调用工具");
            } else {
                try {
                    ragChunks = ragModule.retrieve(dto.getContent(),
                            aiProperties.getRag().getTopK(),
                            aiProperties.getRag().getSimilarityThreshold());
                    if (ragChunks.isEmpty()) {
                        log.info("│ [Step3] RAG检索: 未命中（相似度低于阈值{}），LLM将基于通用能力回答",
                                aiProperties.getRag().getSimilarityThreshold());
                    } else {
                        log.info("│ [Step3] RAG检索: 命中{}条文档片段", ragChunks.size());
                        for (int i = 0; i < ragChunks.size(); i++) {
                            DocumentChunk chunk = ragChunks.get(i);
                            String preview = chunk.getContent().length() > 40
                                    ? chunk.getContent().substring(0, 40) + "..." : chunk.getContent();
                            log.info("│         [{}] score={} content=\"{}\"",
                                    i + 1,
                                    chunk.getScore() != null ? String.format("%.3f", chunk.getScore()) : "N/A",
                                    preview);
                        }
                    }
                } catch (Exception e) {
                    log.warn("│ [Step3] RAG检索失败: {}", e.getMessage());
                }
            }

            // Step 4: 构建 LLM Prompt
            List<ChatMessage> messages = buildPrompt(dto.getContent(), history, ragChunks);
            log.info("│ [Step4] 构建Prompt: messages={}条, withTools={}, ragChunks={}条",
                    messages.size(), withTools, ragChunks.size());

            // Step 5: 调用 LLM（含 Function Calling + 质量校验，推送前完成）
            String finalReply = callLlmWithFunctionCalling(
                    sessionId, messageId, emitter, messages, userContext, dto.getContent(),
                    withTools, history, ragChunks);

            // Step 6: 保存助手回复
            if (finalReply != null && !finalReply.isBlank()) {
                conversationManager.appendMessage(sessionId, ChatMessage.builder()
                        .role("assistant").content(finalReply).build());
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("│ [Step6] 回复已保存, 耗时={}ms", elapsed);
            log.info("└─────────────────────────────────────────────────────");

        } catch (Exception e) {
            log.error("processAndStream error for session {}: {}", sessionId, e.getMessage(), e);
            sendSseEvent(emitter, "error", SseEventVO.builder()
                    .type(SseEventVO.EventType.ERROR)
                    .errorCode("LLM_ERROR")
                    .errorMessage("抱歉，我暂时无法回答，请稍后再试")
                    .build());
            emitter.complete();
        } finally {
            // 无论成功、失败、超时，都必须释放全局并发槽位
            rateLimiter.releaseGlobal(userId);
        }
    }

    /**
     * 调用 LLM，处理 Function Calling，质量校验，最后推送 SSE token
     * 质量校验在推送之前完成，确保推送给前端的内容是通过校验的
     *
     * @param withTools 是否携带工具定义
     * @param history   对话历史，用于 RAG 兜底时重建 Prompt
     * @param ragChunks 当前已检索的 RAG 片段，用于质量校验重试时重建 Prompt
     */
    private String callLlmWithFunctionCalling(String sessionId, String messageId,
                                               SseEmitter emitter, List<ChatMessage> messages,
                                               UserContext userContext, String originalQuestion,
                                               boolean withTools, List<ChatMessage> history,
                                               List<DocumentChunk> ragChunks) {
        List<Map<String, Object>> tools = null;
        if (withTools) {
            tools = functionCallingEngine.getAvailableTools().stream()
                    .map(tool -> {
                        try {
                            String json = objectMapper.writeValueAsString(tool);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = objectMapper.readValue(json, Map.class);
                            return map;
                        } catch (Exception e) {
                            log.warn("Failed to serialize tool definition: {}", e.getMessage());
                            return null;
                        }
                    })
                    .filter(t -> t != null)
                    .collect(Collectors.toList());
        }

        // 只调一次同步 LLM，判断是否 Function Call
        LLMResponse syncResponse = llmEngine.chatSync(LLMRequest.builder()
                .messages(messages).tools(tools).stream(false).build());

        String finalContent = null;

        if (syncResponse.isFunctionCall()) {
            log.info("│ [Step5] LLM决策: 调用工具 → {}", syncResponse.getFunctionName());
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> argsMap = objectMapper.readValue(
                        syncResponse.getFunctionArguments() != null
                                ? syncResponse.getFunctionArguments() : "{}",
                        Map.class);

                FunctionCall functionCall = FunctionCall.builder()
                        .name(syncResponse.getFunctionName())
                        .rawArguments(syncResponse.getFunctionArguments())
                        .arguments(argsMap)
                        .build();

                log.info("│         工具参数: {}", syncResponse.getFunctionArguments());

                sendSseEvent(emitter, "function", SseEventVO.builder()
                        .type(SseEventVO.EventType.FUNCTION)
                        .functionName(functionCall.getName())
                        .functionStatus("calling")
                        .build());

                FunctionResult result = functionCallingEngine.execute(functionCall, userContext);
                log.info("│         工具执行结果: success={} data={}",
                        result.isSuccess(),
                        result.isSuccess()
                                ? (result.getData() != null && result.getData().length() > 80
                                        ? result.getData().substring(0, 80) + "..." : result.getData())
                                : result.getErrorMessage());

                List<ChatMessage> messagesWithResult = new ArrayList<>(messages);
                messagesWithResult.add(ChatMessage.builder()
                        .role("assistant")
                        .content("调用工具: " + functionCall.getName())
                        .build());
                String toolResultContent = result.isSuccess()
                        ? "工具 " + functionCall.getName() + " 返回结果：" + objectMapper.writeValueAsString(result.getData())
                        : "工具 " + functionCall.getName() + " 执行失败：" + result.getErrorMessage();
                messagesWithResult.add(ChatMessage.builder()
                        .role("user")
                        .content(toolResultContent)
                        .build());

                LLMResponse toolReply = llmEngine.chatSync(LLMRequest.builder()
                        .messages(messagesWithResult).tools(null).stream(false).build());
                finalContent = toolReply.getContent();

            } catch (Exception e) {
                log.error("Function calling failed: {}", e.getMessage(), e);
            }
        } else {
            // 普通文本回复
            String content = syncResponse.getContent();
            if (withTools && (content == null || content.isBlank())) {
                // 带了工具但 LLM 没调、且回复为空 → RAG 兜底
                log.info("│ [Step5] LLM带工具但未调用且回复为空，触发RAG兜底");
                try {
                    List<DocumentChunk> fallbackChunks = ragModule.retrieve(originalQuestion,
                            aiProperties.getRag().getTopK(),
                            aiProperties.getRag().getSimilarityThreshold());
                    log.info("│         RAG兜底检索: 命中{}条", fallbackChunks.size());
                    List<ChatMessage> fallbackMessages = buildPrompt(originalQuestion,
                            messages.subList(1, Math.max(1, messages.size() - 1)),
                            fallbackChunks);
                    LLMResponse fallbackResponse = llmEngine.chatSync(LLMRequest.builder()
                            .messages(fallbackMessages).tools(null).stream(false).build());
                    content = fallbackResponse.getContent();
                    if (content != null && !content.isBlank()) {
                        log.info("│         RAG兜底回复成功");
                    }
                } catch (Exception e) {
                    log.warn("│         RAG兜底失败: {}", e.getMessage());
                }
            } else {
                log.info("│ [Step5] LLM决策: 直接文本回复（未调用工具）");
            }
            finalContent = content;
        }

        // ── 质量校验（在推送之前完成，确保推送的是通过校验的内容）──
        if (finalContent != null && !finalContent.isBlank()) {
            int maxRetry = aiProperties.getQuality().getMaxRetryCount();
            for (int retry = 0; retry < maxRetry; retry++) {
                QualityCheckResult qcResult = qualityGuard.checkRelevance(originalQuestion, finalContent);
                if (qcResult.isPassed()) {
                    log.info("│ [Step6] 质量校验通过: score={}", String.format("%.2f", qcResult.getScore()));
                    break;
                }
                log.info("│ [Step6] 质量校验不通过(retry {}/{}): score={}, 重新生成",
                        retry + 1, maxRetry, String.format("%.2f", qcResult.getScore()));
                List<ChatMessage> retryMessages = buildRetryPrompt(originalQuestion, history, ragChunks);
                finalContent = callLlmSync(retryMessages);
            }
        }

        // ── 推送最终内容给前端 ──
        if (finalContent != null && !finalContent.isBlank()) {
            sendSseEvent(emitter, "token", SseEventVO.builder()
                    .type(SseEventVO.EventType.TOKEN).content(finalContent).build());
        } else {
            // 最终兜底话术
            String fallback = "抱歉，我暂时无法回答您的问题，建议您联系人工客服获取帮助。";
            log.info("│ [Step5] 所有路径均无结果，返回兜底话术");
            finalContent = fallback;
            sendSseEvent(emitter, "token", SseEventVO.builder()
                    .type(SseEventVO.EventType.TOKEN).content(fallback).build());
        }

        sendSseEvent(emitter, "done", SseEventVO.builder()
                .type(SseEventVO.EventType.DONE).messageId(messageId).build());
        emitter.complete();

        return finalContent;
    }

    /**
     * 同步调用 LLM（用于质量校验重试）
     */
    private String callLlmSync(List<ChatMessage> messages) {
        LLMResponse response = llmEngine.chatSync(LLMRequest.builder()
                .messages(messages).stream(false).build());
        return response.getContent();
    }

    /**
     * 构建 LLM Prompt：System Prompt + RAG 上下文 + 对话历史 + 用户消息
     * 将原始用户问题作为约束条件注入（满足需求 7.5.15）
     */
    private List<ChatMessage> buildPrompt(String userMessage, List<ChatMessage> history,
                                           List<DocumentChunk> ragChunks) {
        List<ChatMessage> messages = new ArrayList<>();

        // System Prompt + RAG 上下文注入
        StringBuilder systemContent = new StringBuilder(OpenAILLMEngine.CUSTOMER_SERVICE_SYSTEM_PROMPT);
        if (!ragChunks.isEmpty()) {
            systemContent.append("\n\n【知识库内容 - 你必须且只能基于以下内容回答，不得使用任何其他信息】\n");
            for (DocumentChunk chunk : ragChunks) {
                systemContent.append("---\n").append(chunk.getContent()).append("\n");
            }
            systemContent.append("---\n以上是全部知识库内容，请严格基于上述内容回答用户问题。");
        } else {
            systemContent.append("\n\n【注意】当前问题在知识库中无相关内容，请回答：\"抱歉，暂无该问题的相关资料，建议您联系人工客服进一步咨询。\"");
        }
        messages.add(ChatMessage.builder().role("system").content(systemContent.toString()).build());

        // 对话历史（截断到上下文窗口限制）
        messages.addAll(history);

        // 当前用户消息
        messages.add(ChatMessage.builder().role("user").content(userMessage).build());

        return messages;
    }

    /**
     * 构建重试 Prompt（强调原始问题，避免偏题）
     */
    private List<ChatMessage> buildRetryPrompt(String userMessage, List<ChatMessage> history,
                                                List<DocumentChunk> ragChunks) {
        List<ChatMessage> messages = buildPrompt(userMessage, history, ragChunks);
        // 在用户消息后追加强调约束
        int lastIdx = messages.size() - 1;
        ChatMessage lastMsg = messages.get(lastIdx);
        messages.set(lastIdx, ChatMessage.builder()
                .role("user")
                .content(lastMsg.getContent() + "\n\n[请确保回答直接针对上述问题，不要偏题]")
                .build());
        return messages;
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, SseEventVO data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException e) {
            log.warn("Failed to send SSE event {}: {}", eventName, e.getMessage());
        }
    }

    /**
     * 等待 SSE emitter 就绪（前端可能稍晚订阅）
     * 最多等待 3 秒
     */
    private SseEmitter waitForEmitter(String sessionId) {
        for (int i = 0; i < 100; i++) {
            SseEmitter emitter = emitterMap.get(sessionId);
            if (emitter != null) return emitter;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return emitterMap.get(sessionId);
    }

    /**
     * 判断是否为实时数据类意图（需要调工具，不需要 RAG）
     * 这类问题的答案在数据库里，知识库里没有
     */
    private boolean isRealTimeDataIntent(String intent) {
        return "query_order_status".equals(intent)
                || "query_stock_status".equals(intent)
                || "list_active_seckill".equals(intent)
                || "list_user_vouchers".equals(intent);
    }

    /**
     * 判断是否为纯知识库意图（只需要 RAG，不需要工具）
     * knowledge_qa：明确的知识库问答意图（规则引擎命中 或 LLM识别）
     * unknown：意图无法识别，保守处理，走RAG兜底，避免LLM编造
     * tool_call：LLM识别为需要调工具，不走RAG
     */
    private boolean isKnowledgeOnlyIntent(String intent) {
        return "knowledge_qa".equals(intent) || "unknown".equals(intent);
    }
}
