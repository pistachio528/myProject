package com.hmdp.ai.engine.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.domain.UserContext;
import com.hmdp.ai.domain.dto.FunctionResult;
import com.hmdp.ai.domain.dto.QualityCheckResult;
import com.hmdp.ai.domain.dto.ValidationResult;
import com.hmdp.ai.engine.LLMEngine;
import com.hmdp.ai.engine.QualityGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 回复质量校验实现
 * <p>
 * checkRelevance：调用 LLM 评估回复与问题的相关性，低于阈值（默认 0.6）时返回不通过
 * validateFunctionResult：验证工具函数返回结果归属当前用户，检查 error 字段
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityGuardImpl implements QualityGuard {

    private final LLMEngine llmEngine;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public QualityCheckResult checkRelevance(String originalQuestion, String reply) {
        try {
            double score = llmEngine.evaluateRelevance(originalQuestion, reply);
            double threshold = aiProperties.getQuality().getRelevanceThreshold();
            boolean passed = score >= threshold;
            return QualityCheckResult.builder()
                    .passed(passed)
                    .score(score)
                    .reason(passed ? null
                            : "回复与问题相关性不足（分数：" + score + "，阈值：" + threshold + "）")
                    .build();
        } catch (Exception e) {
            log.warn("Relevance check failed, defaulting to passed to avoid blocking reply. Error: {}",
                    e.getMessage());
            // 评估失败时默认通过，避免阻塞正常回复
            return QualityCheckResult.builder()
                    .passed(true)
                    .score(0.5)
                    .reason("评估失败，默认通过")
                    .build();
        }
    }

    @Override
    public ValidationResult validateFunctionResult(FunctionResult result, UserContext context) {
        // 检查工具函数是否执行成功
        if (!result.isSuccess()) {
            return ValidationResult.builder()
                    .valid(false)
                    .reason("工具函数执行失败：" + result.getErrorMessage())
                    .build();
        }

        // 检查返回数据是否为空
        if (result.getData() == null || result.getData().isBlank()) {
            return ValidationResult.builder()
                    .valid(false)
                    .reason("工具函数返回数据为空")
                    .build();
        }

        try {
            JsonNode dataNode = objectMapper.readTree(result.getData());

            // 检查是否包含 error 字段（表示查询失败）
            if (dataNode.has("error")) {
                return ValidationResult.builder()
                        .valid(false)
                        .reason("工具函数返回错误：" + dataNode.path("error").asText())
                        .build();
            }

            // 检查 userId 归属（如果结果中包含 userId 字段）
            if (dataNode.has("userId") && context != null && context.getUserId() != null) {
                long resultUserId = dataNode.path("userId").asLong();
                if (resultUserId != context.getUserId()) {
                    log.warn("Function result userId {} does not match context userId {}",
                            resultUserId, context.getUserId());
                    return ValidationResult.builder()
                            .valid(false)
                            .reason("查询结果不属于当前用户")
                            .build();
                }
            }

            return ValidationResult.builder().valid(true).build();

        } catch (Exception e) {
            log.warn("Failed to parse function result for validation, defaulting to valid. Error: {}",
                    e.getMessage());
            // 解析失败时默认通过，避免误拦截正常结果
            return ValidationResult.builder().valid(true).build();
        }
    }
}
