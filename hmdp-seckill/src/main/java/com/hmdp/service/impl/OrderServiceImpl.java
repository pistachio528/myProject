package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.dto.SeckillMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.TokenStatus;
import java.util.List;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现
 * <p>
 * 异步消费 RabbitMQ 消息，创建秒杀订单，更新 token 状态。
 * 幂等保证：数据库唯一索引 uk_user_voucher（第二道防线）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final VoucherOrderMapper voucherOrderMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 消费秒杀订单消息，异步创建订单
     * 手动 ACK 模式：成功 ACK，异常 NACK（触发 RabbitMQ 重试，最多 3 次后进死信队列）
     */
    @Override
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void consumeOrderMessage(String messageJson, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            // 解析消息
            SeckillMessage message = objectMapper.readValue(messageJson, SeckillMessage.class);
            log.info("[OrderService] 收到秒杀消息，userId={}，voucherId={}，token={}",
                    message.getUserId(), message.getVoucherId(), message.getToken());

            // 创建订单
            VoucherOrder order = new VoucherOrder();
            order.setUserId(message.getUserId());
            order.setVoucherId(message.getVoucherId());
            order.setStatus(1); // 1=处理中

            try {
                voucherOrderMapper.insert(order);
            } catch (DuplicateKeyException e) {
                // 唯一索引冲突：订单已存在（幂等处理）
                log.warn("[OrderService] 订单已存在（幂等），userId={}，voucherId={}",
                        message.getUserId(), message.getVoucherId());
                // 更新 token 状态为 CREATED
                updateTokenStatus(message.getToken(), TokenStatus.CREATED);
                channel.basicAck(tag, false);
                return;
            }

            // 订单插入成功，同步扣减数据库库存
            int affected = seckillVoucherMapper.decrementStock(message.getVoucherId());
            if (affected == 0) {
                // 库存扣减失败（DB 库存已为 0，与 Redis 不一致），抛出异常触发 NACK 重试
                // 重试超限后进死信队列，由 StockManager 回补 Redis 库存
                log.error("[OrderService] 数据库库存扣减失败，voucherId={}，触发 NACK 重试", message.getVoucherId());
                throw new IllegalStateException("数据库库存扣减失败，voucherId=" + message.getVoucherId());
            }

            // 更新订单状态为已创建（status=2）
            order.setStatus(2);
            voucherOrderMapper.updateById(order);

            // 更新 token 状态为 CREATED，TTL 重置 5min
            updateTokenStatus(message.getToken(), TokenStatus.CREATED);
            channel.basicAck(tag, false);
            log.info("[OrderService] 订单创建成功，orderId={}，token={}", order.getId(), message.getToken());

        } catch (Exception e) {
            log.error("[OrderService] 消费消息异常，消息：{}，原因：{}", messageJson, e.getMessage(), e);
            try {
                // NACK，不重新入队（requeue=false），让 RabbitMQ 按重试策略处理
                channel.basicNack(tag, false, false);
            } catch (Exception ex) {
                log.error("[OrderService] NACK 失败", ex);
            }
        }
    }

    @Override
    public List<VoucherOrder> listMyOrders(Long userId) {
        return voucherOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getUserId, userId)
                        .orderByDesc(VoucherOrder::getCreateTime)
        );
    }

    /**
     * 查询订单进度（通过排队 token）
     * key 不存在（过期）时返回 QUERY_TIMEOUT 提示
     */
    @Override
    public String queryOrderStatus(String token) {
        String key = SeckillRedisKey.orderStatus(token);
        String status = stringRedisTemplate.opsForValue().get(key);
        if (status == null) {
            // token 已过期，引导用户查订单列表
            return TokenStatus.QUEUING.name() + "_TIMEOUT:查询超时，请前往订单列表确认";
        }
        return status;
    }

    /**
     * 更新 Redis token 状态，TTL 重置为 5 分钟
     */
    private void updateTokenStatus(String token, TokenStatus status) {
        String key = SeckillRedisKey.orderStatus(token);
        stringRedisTemplate.opsForValue().set(key, status.name(), 5, TimeUnit.MINUTES);
    }
}
