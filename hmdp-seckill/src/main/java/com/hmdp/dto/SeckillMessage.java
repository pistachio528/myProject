package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀 MQ 消息体
 * <p>
 * 在 RabbitMQ 中传递的消息载体，包含创建订单所需的最小信息集。
 * 序列化为 JSON 格式传输。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage {

    /** 用户ID */
    private Long userId;

    /** 优惠券ID */
    private Long voucherId;

    /** 排队 token（UUID，用于更新订单进度状态） */
    private String token;
}
