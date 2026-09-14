package com.hmdp.ai.engine;

import com.hmdp.ai.domain.UserContext;
import com.hmdp.ai.domain.dto.FunctionCall;
import com.hmdp.ai.domain.dto.FunctionResult;
import com.hmdp.ai.domain.dto.ToolDefinition;

import java.util.List;

/**
 * Function Calling 引擎接口
 * 负责工具函数的注册、参数补全、执行和结果验证
 */
public interface FunctionCallingEngine {

    /**
     * 执行工具函数调用
     * @param functionCall LLM 输出的函数调用指令
     * @param userContext  用户上下文（含 userId、当前页面等）
     * @return 工具执行结果
     */
    FunctionResult execute(FunctionCall functionCall, UserContext userContext);

    /**
     * 获取所有可用工具的定义列表（用于 LLM tools 参数）
     */
    List<ToolDefinition> getAvailableTools();

    /**
     * 自动补全工具函数参数
     * 从 UserContext 中自动填充 user_id 等必填参数
     * @return 补全后的 FunctionCall
     */
    FunctionCall autoCompleteParams(FunctionCall functionCall, UserContext userContext);
}
