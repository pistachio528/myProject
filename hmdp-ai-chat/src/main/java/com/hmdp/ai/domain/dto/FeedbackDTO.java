package com.hmdp.ai.domain.dto;

import lombok.Data;

@Data
public class FeedbackDTO {
    private String sessionId;
    private Long messageId;
    private Long userId;
    /** wrong_entity/irrelevant/inaccurate/other */
    private String feedbackType;
    private String detail;
}
