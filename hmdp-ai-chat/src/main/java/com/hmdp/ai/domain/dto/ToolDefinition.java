package com.hmdp.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具函数定义（用于 LLM tools 参数）
 * 遵循 OpenAI Function Calling 格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    /** 固定为 "function" */
    private String type;
    /** 函数定义 */
    private FunctionDef function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDef {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }
}
