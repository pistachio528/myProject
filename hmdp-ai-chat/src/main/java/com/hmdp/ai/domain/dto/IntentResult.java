package com.hmdp.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 意图识别结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {
    /** 识别到的意图（如 query_order_status / query_voucher_info / knowledge_qa / unknown） */
    private String intent;
    /** 置信度分数 0.0 ~ 1.0 */
    private double confidence;
    /** 识别到的实体列表 */
    private List<Entity> entities;
    /** LLM 的推理说明 */
    private String reasoning;
    /** 是否需要用户确认（置信度不足时） */
    private boolean needsConfirmation;
    /** 澄清问题（needsConfirmation=true 时） */
    private String clarificationQuestion;
    /** 候选实体列表（消歧时） */
    private List<Entity> candidateEntities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entity {
        /** 实体类型：order_token / voucher_id / voucher_name / activity_name */
        private String type;
        /** 实体值 */
        private String value;
        /** 来源：user_message / context / history */
        private String source;
    }
}
