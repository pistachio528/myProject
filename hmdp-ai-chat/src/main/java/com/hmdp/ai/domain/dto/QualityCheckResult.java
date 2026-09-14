package com.hmdp.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回复质量校验结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityCheckResult {
    /** 是否通过校验 */
    private boolean passed;
    /** 相关性分数 0.0 ~ 1.0 */
    private double score;
    /** 失败原因（passed=false 时） */
    private String reason;
}
