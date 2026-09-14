package com.hmdp.service;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 消费 RabbitMQ 订单消息，异步创建订单
     */
    void consumeOrderMessage(String messageJson, com.rabbitmq.client.Channel channel, long tag);

    /**
     * 查询订单进度（通过排队 token）
     */
    String queryOrderStatus(String token);

    /**
     * 查询当前用户的订单列表
     */
    java.util.List<com.hmdp.entity.VoucherOrder> listMyOrders(Long userId);
}
