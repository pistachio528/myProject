package com.hmdp.ai.domain.dto;

import lombok.Data;

/**
 * 发送消息请求 DTO
 */
@Data
public class SendMessageDTO {
    /** 会话 ID（为空时自动创建新会话） */
    private String sessionId;
    /** 用户消息内容 */
    private String content;
    /** 用户 ID（从 token 解析，此处简化为请求参数） */
    private Long userId;
    /** 当前页面上下文（用于意图消歧） */
    private String pageContext;
    /** 前端登录 token，用于后端解析真实 userId */
    private String authToken;
}
