package com.hmdp.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息（用于 LLM 上下文传递）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /** 角色：system / user / assistant / tool */
    private String role;
    /** 消息内容 */
    private String content;
    /** 消息类型：text / card / function_call（默认 text） */
    @Builder.Default
    private String messageType = "text";
    /** 创建时间戳（毫秒） */
    private Long createTime;
}
