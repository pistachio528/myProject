package com.hmdp.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 意图识别日志实体
 * 对应 intent_log 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("intent_log")
public class IntentLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 用户原始消息 */
    private String userMessage;

    /** 识别到的意图 */
    private String intent;

    /** 置信度 0.00-1.00，对应 DECIMAL(3,2) */
    private BigDecimal confidence;

    /** 是否触发消歧：0=否 1=自动消歧 2=用户确认 */
    private Integer disambiguation;

    /** 是否触发用户确认：0=否 1=是 */
    private Integer userConfirmed;

    /** JSON 格式的上下文信号 */
    private String contextSignals;

    /** 创建时间 */
    private LocalDateTime createTime;
}
