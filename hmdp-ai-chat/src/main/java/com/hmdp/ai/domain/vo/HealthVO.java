package com.hmdp.ai.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class HealthVO {
    /** 整体状态：UP / DOWN / DEGRADED */
    private String status;
    /** 各组件状态 */
    private Map<String, ComponentStatus> components;
    /** 检查时间 */
    private LocalDateTime timestamp;

    @Data
    @Builder
    public static class ComponentStatus {
        /** UP / DOWN */
        private String status;
        /** 附加信息（如延迟、版本等） */
        private String detail;
    }
}
