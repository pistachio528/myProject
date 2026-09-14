package com.hmdp.ai.engine;

import com.hmdp.ai.domain.UserContext;
import com.hmdp.ai.domain.dto.FunctionResult;
import com.hmdp.ai.domain.dto.QualityCheckResult;
import com.hmdp.ai.domain.dto.ValidationResult;

/**
 * 回复质量校验接口
 */
public interface QualityGuard {

    /**
     * 检查回复与原始问题的相关性
     *
     * @param originalQuestion 用户原始问题
     * @param reply            LLM 生成的回复
     * @return 校验结果（通过/不通过 + 分数）
     */
    QualityCheckResult checkRelevance(String originalQuestion, String reply);

    /**
     * 验证工具函数返回结果的正确性
     *
     * @param result  工具函数执行结果
     * @param context 用户上下文
     * @return 校验结果（结果是否归属当前用户）
     */
    ValidationResult validateFunctionResult(FunctionResult result, UserContext context);
}
