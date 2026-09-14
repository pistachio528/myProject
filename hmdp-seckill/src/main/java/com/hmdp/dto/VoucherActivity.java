package com.hmdp.dto;

import com.hmdp.enums.LimitType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动信息 DTO
 * <p>
 * 用于在 Redis 活动缓存与业务层之间传递活动配置信息，
 * 包含限购规则、时间窗口等核心字段。
 * </p>
 */
@Data
public class VoucherActivity {

    /** 优惠券ID */
    private Long voucherId;

    /** 限购类型 */
    private LimitType limitType;

    /** 最大购买次数（ONE_PER_USER/ONE_PER_DAY 时为 1，N_PER_USER 时为 N） */
    private Integer maxCount;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;
}
