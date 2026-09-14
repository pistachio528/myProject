package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀优惠券实体
 */
@Data
@TableName("seckill_voucher")
public class SeckillVoucher {

    /** 优惠券ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题 */
    private String title;

    /** 副标题 */
    private String subTitle;

    /** 库存 */
    private Integer stock;

    /** 支付金额（分） */
    private Long payValue;

    /** 实际价值（分） */
    private Long actualValue;

    /** 活动开始时间 */
    private LocalDateTime beginTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 类型：1=秒杀券 */
    private Integer type;

    /** 限购类型：ONE_PER_USER / ONE_PER_DAY / N_PER_USER（默认 ONE_PER_USER） */
    private String limitType;

    /** 最大购买次数（N_PER_USER 时有效，默认 1） */
    private Integer maxCount;
}
