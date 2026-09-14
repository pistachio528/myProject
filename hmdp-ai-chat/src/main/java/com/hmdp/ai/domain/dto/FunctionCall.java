package com.hmdp.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * LLM 输出的工具函数调用指令
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionCall {
    /** 函数名称 */
    private String name;
    /** 函数参数（已解析的 Map） */
    private Map<String, Object> arguments;
    /** 原始参数 JSON 字符串 */
    private String rawArguments;
}
