package com.hmdp.enums;

/**
 * 排队 Token 状态枚举
 * <p>
 * 状态单向流转：QUEUING → CREATED（订单创建成功）
 *                       → FAILED（订单创建失败，库存已回补）
 * Token 过期后 Redis key 自动删除，查询时返回 QUERY_TIMEOUT 提示。
 * </p>
 */
public enum TokenStatus {

    /** 排队中：秒杀成功，等待异步消费者创建订单 */
    QUEUING,

    /** 创建成功：MQ 消费者已成功写入 voucher_order 表 */
    CREATED,

    /** 创建失败：MQ 消费失败超过重试次数，库存已由死信消费者回补 */
    FAILED
}
