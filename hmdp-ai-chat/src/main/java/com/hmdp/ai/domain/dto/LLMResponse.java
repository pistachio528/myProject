package com.hmdp.ai.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * LLM 同步调用响应
 */
@Data
@Builder
public class LLMResponse {
    /** 回复文本内容 */
    private String content;
    /** Function Call 名称（如有） */
    private String functionName;
    /** Function Call 参数 JSON 字符串（如有） */
    private String functionArguments;
    /** 是否为 Function Call 响应 */
    private boolean functionCall;
    /** 输入 token 数 */
    private Integer promptTokens;
    /** 输出 token 数 */
    private Integer completionTokens;
    /** 完成原因：stop / function_call / length */
    private String finishReason;
}
