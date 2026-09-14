package com.hmdp.ai.domain.dto;

import com.hmdp.ai.domain.ChatMessage;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用请求
 */
@Data
@Builder
public class LLMRequest {
    /** 对话历史（含 system prompt） */
    private List<ChatMessage> messages;
    /** 工具定义列表（Function Calling 用） */
    private List<Map<String, Object>> tools;
    /** 温度参数（0.0~2.0，默认 0.7） */
    @Builder.Default
    private Double temperature = 0.7;
    /** 最大输出 token 数 */
    @Builder.Default
    private Integer maxTokens = 2048;
    /** 是否流式输出 */
    @Builder.Default
    private Boolean stream = false;
}
