package com.hmdp.service;

import com.hmdp.dto.SeckillMessage;

/**
 * 消息中继接口（本地消息表可靠投递）
 * <p>
 * 通过本地消息表将 Redis 库存扣减与 MQ 投递绑定在同一本地事务，
 * 保证"库存扣了必有消息"的一致性。
 * </p>
 */
public interface MessageRelay {

    /**
     * 在当前事务中写入本地消息表，状态=PENDING
     * <p>
     * 必须在 @Transactional 方法中调用，与业务数据同事务提交。
     * </p>
     *
     * @param message 消息内容
     */
    void saveMessage(SeckillMessage message);

    /**
     * 投递消息至 RabbitMQ，成功后更新状态=SENT
     * <p>
     * 投递失败时保持 PENDING，由 retryPendingMessages 定时重试。
     * </p>
     *
     * @param message 消息内容
     */
    void relay(SeckillMessage message);

    /**
     * 定时重试：扫描 PENDING 且超时的消息，重新投递
     * <p>
     * 每 30 秒执行一次，扫描 createTime < now-1min 的 PENDING 消息。
     * retryCount >= 5 时更新状态=DEAD 并告警，不再重试。
     * </p>
     */
    void retryPendingMessages();
}
