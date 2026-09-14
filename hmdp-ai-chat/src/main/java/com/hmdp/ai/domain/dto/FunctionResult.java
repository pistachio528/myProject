package com.hmdp.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具函数执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionResult {
    /** 函数名称 */
    private String functionName;
    /** 执行是否成功 */
    private boolean success;
    /** 结果数据（JSON 字符串，回传给 LLM） */
    private String data;
    /** 错误信息（执行失败时） */
    private String errorMessage;
    /** 错误码 */
    private String errorCode;
}
