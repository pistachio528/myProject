package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置
 * <p>
 * 拓扑结构：
 * <pre>
 * 生产者
 *   └─▶ seckill.order.exchange (Direct)
 *         └─▶ seckill.order.queue  ──[消费失败超3次]──▶ seckill.order.dlx
 *                                                           └─▶ seckill.order.dlq
 * </pre>
 * </p>
 */
@Configuration
public class RabbitMQConfig {

    // ==================== Exchange 名称 ====================

    /** 秒杀订单主 Exchange（Direct） */
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";

    /** 死信 Exchange（Dead Letter Exchange） */
    public static final String SECKILL_ORDER_DLX = "seckill.order.dlx";

    // ==================== Queue 名称 ====================

    /** 秒杀订单主队列 */
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";

    /** 死信队列（Dead Letter Queue） */
    public static final String SECKILL_ORDER_DLQ = "seckill.order.dlq";

    // ==================== Routing Key ====================

    /** 主队列路由键 */
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    /** 死信队列路由键 */
    public static final String SECKILL_DLQ_ROUTING_KEY = "seckill.order.dead";

    // ==================== Bean 定义 ====================

    /**
     * 秒杀订单主 Exchange（Direct 类型，持久化）
     */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return ExchangeBuilder.directExchange(SECKILL_ORDER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 死信 Exchange（Direct 类型，持久化）
     */
    @Bean
    public DirectExchange seckillOrderDlx() {
        return ExchangeBuilder.directExchange(SECKILL_ORDER_DLX)
                .durable(true)
                .build();
    }

    /**
     * 秒杀订单主队列
     * <p>
     * 配置说明：
     * - x-dead-letter-exchange：消费失败后路由到死信 Exchange
     * - x-dead-letter-routing-key：死信消息的路由键
     * - x-max-delivery-count：最大投递次数（含首次），超过后转入死信队列
     * </p>
     */
    @Bean
    public Queue seckillOrderQueue() {
        Map<String, Object> args = new HashMap<>();
        // 死信路由配置
        args.put("x-dead-letter-exchange", SECKILL_ORDER_DLX);
        args.put("x-dead-letter-routing-key", SECKILL_DLQ_ROUTING_KEY);
        // 最大投递次数：首次 + 重试 2 次 = 共 3 次
        args.put("x-max-delivery-count", 3);
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .withArguments(args)
                .build();
    }

    /**
     * 死信队列（持久化，不再配置 DLX，避免无限循环）
     */
    @Bean
    public Queue seckillOrderDlq() {
        return QueueBuilder.durable(SECKILL_ORDER_DLQ).build();
    }

    /**
     * 主队列绑定到主 Exchange
     */
    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillOrderExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(seckillOrderExchange)
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    /**
     * 死信队列绑定到死信 Exchange
     */
    @Bean
    public Binding seckillDlqBinding(Queue seckillOrderDlq, DirectExchange seckillOrderDlx) {
        return BindingBuilder.bind(seckillOrderDlq)
                .to(seckillOrderDlx)
                .with(SECKILL_DLQ_ROUTING_KEY);
    }
}
