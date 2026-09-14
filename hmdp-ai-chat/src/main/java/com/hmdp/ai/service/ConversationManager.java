package com.hmdp.ai.service;

import com.hmdp.ai.domain.ChatMessage;
import com.hmdp.ai.domain.UserContext;
import java.util.List;

/**
 * 对话管理器接口
 * 负责会话生命周期、对话历史存储和用户上下文管理
 */
public interface ConversationManager {

    /**
     * 创建新会话
     * @param userId 用户 ID
     * @return 新会话 ID（UUID）
     */
    String createSession(Long userId);

    /**
     * 获取会话的最近 N 轮对话历史
     * @param sessionId 会话 ID
     * @param maxRounds 最大轮数（0 表示使用配置默认值）
     * @return 按时间顺序排列的消息列表
     */
    List<ChatMessage> getHistory(String sessionId, int maxRounds);

    /**
     * 追加消息到会话
     * @param sessionId 会话 ID
     * @param message   消息
     */
    void appendMessage(String sessionId, ChatMessage message);

    /**
     * 结束会话并归档到 MySQL
     * @param sessionId 会话 ID
     */
    void endSession(String sessionId);

    /**
     * 获取用户上下文信号（用于意图消歧）
     * @param userId    用户 ID
     * @param sessionId 当前会话 ID
     * @return 用户上下文
     */
    UserContext getUserContext(Long userId, String sessionId);

    /**
     * 获取用户当前活跃会话 ID
     * @param userId 用户 ID
     * @return 活跃会话 ID，不存在则返回 null
     */
    String getActiveSessionId(Long userId);
}
