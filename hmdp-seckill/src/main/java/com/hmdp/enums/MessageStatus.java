package com.hmdp.enums;

/**
 * 本地消息表状态枚举
 * <p>
 * 状态流转：PENDING → SENT（投递成功）
 *                   → DEAD（重试超限）→ REPLENISHED（库存已回补）
 * </p>
 */
public enum MessageStatus {

    /** 待发送：消息已写入本地消息表，尚未成功投递至 MQ */
    PENDING,

    /** 已发送：消息已成功投递至 RabbitMQ */
    SENT,

    /** 死信：重试次数超过阈值（5次），不再自动重试，需人工介入 */
    DEAD,

    /** 已回补：死信消费者已将库存回补至 Redis */
    REPLENISHED
}
