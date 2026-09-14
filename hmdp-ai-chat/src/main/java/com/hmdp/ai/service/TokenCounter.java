package com.hmdp.ai.service;

import com.hmdp.ai.domain.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Token 计数与截断工具
 * 使用简单字符估算（中文字符 ≈ 1.5 token，英文单词 ≈ 1 token）
 * 需求 4.3：当对话历史 token 超过上下文窗口限制时，截断最早的消息
 */
@Component
public class TokenCounter {

    /** 简单估算：每个字符约 0.75 token（中英文混合场景的保守估算） */
    private static final double CHARS_PER_TOKEN = 0.75;

    /**
     * 估算消息的 token 数量
     */
    public int estimateTokens(ChatMessage message) {
        if (message == null || message.getContent() == null) return 0;
        return (int) Math.ceil(message.getContent().length() / CHARS_PER_TOKEN);
    }

    /**
     * 估算消息列表的总 token 数量
     */
    public int estimateTotalTokens(List<ChatMessage> messages) {
        return messages.stream().mapToInt(this::estimateTokens).sum();
    }

    /**
     * 截断消息列表，使总 token 数不超过 maxTokens
     * 保留最新的消息（从列表末尾开始保留）
     * system 消息始终保留
     *
     * @param messages  原始消息列表（按时间顺序）
     * @param maxTokens 最大 token 数
     * @return 截断后的消息列表
     */
    public List<ChatMessage> truncateToFit(List<ChatMessage> messages, int maxTokens) {
        if (messages == null || messages.isEmpty()) return messages;

        // 分离 system 消息和普通消息
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> normalMessages = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemMessages.add(msg);
            } else {
                normalMessages.add(msg);
            }
        }

        int systemTokens = estimateTotalTokens(systemMessages);
        int remainingTokens = maxTokens - systemTokens;

        // 从最新消息开始，逐步加入直到超出限制
        List<ChatMessage> kept = new ArrayList<>();
        for (int i = normalMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = normalMessages.get(i);
            int msgTokens = estimateTokens(msg);
            if (remainingTokens - msgTokens >= 0) {
                kept.add(0, msg);
                remainingTokens -= msgTokens;
            } else {
                break; // 超出限制，停止添加更早的消息
            }
        }

        // 合并 system 消息和保留的普通消息
        List<ChatMessage> result = new ArrayList<>(systemMessages);
        result.addAll(kept);
        return result;
    }
}
