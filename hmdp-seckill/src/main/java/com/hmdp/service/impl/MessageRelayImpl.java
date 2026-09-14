package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.SeckillMessage;
import com.hmdp.entity.LocalMessage;
import com.hmdp.enums.MessageStatus;
import com.hmdp.mapper.LocalMessageMapper;
import com.hmdp.service.MessageRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息中继实现（本地消息表可靠投递）
 * <p>
 * 核心保证：saveMessage 在调用方事务中执行，与库存扣减同事务提交，
 * 保证"库存扣了必有消息记录"。
 * relay 在事务提交后调用，投递失败由定时任务重试。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRelayImpl implements MessageRelay {

    private final RabbitTemplate rabbitTemplate;
    private final LocalMessageMapper localMessageMapper;
    private final ObjectMapper objectMapper;

    /**
     * 在当前事务中写入本地消息表，状态=PENDING
     * 必须在 @Transactional 方法中调用
     */
    @Override
    public void saveMessage(SeckillMessage message) {
        LocalMessage localMessage = new LocalMessage();
        localMessage.setVoucherId(message.getVoucherId());
        localMessage.setUserId(message.getUserId());
        localMessage.setToken(message.getToken());
        localMessage.setStatus(MessageStatus.PENDING.name());
        localMessage.setRetryCount(0);
        localMessageMapper.insert(localMessage);
        log.debug("[MessageRelay] 本地消息写入成功，token={}", message.getToken());
    }

    /**
     * 投递消息至 RabbitMQ，等待 MQ 确认后才更新状态=SENT；失败保持 PENDING
     * 使用异步线程池执行，不阻塞主链路
     */
    @Override
    @org.springframework.scheduling.annotation.Async("mqRelayExecutor")
    public void relay(SeckillMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            // 使用 invoke 方式发送并等待确认（同步等待 MQ ACK，超时 5 秒）
            rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(
                        RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                        RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                        json
                );
                // 等待 MQ 确认，超时 5 秒。返回 true=确认收到，false=超时未确认
                boolean confirmed = operations.waitForConfirms(5000);
                if (!confirmed) {
                    throw new RuntimeException("MQ 发布确认超时，消息可能未送达");
                }
                return confirmed;
            });

            // MQ 确认收到，更新状态为 SENT
            updateStatus(message.getToken(), MessageStatus.SENT, null);
            log.debug("[MessageRelay] 消息投递成功（已确认），token={}", message.getToken());
        } catch (Exception e) {
            // 投递失败或确认超时，保持 PENDING，由定时任务重试
            log.warn("[MessageRelay] 消息投递失败，token={}，原因：{}，将由定时任务重试",
                    message.getToken(), e.getMessage());
        }
    }

    /**
     * 定时重试：每 30 秒扫描超时 PENDING 消息，重新投递
     * retryCount >= 5 时更新状态=DEAD 并告警
     */
    @Override
    @Scheduled(fixedDelay = 30000)
    public void retryPendingMessages() {
        // 查询 1 分钟前仍处于 PENDING 状态的消息
        java.time.LocalDateTime timeout = java.time.LocalDateTime.now().minusMinutes(1);
        List<LocalMessage> pendingMessages = localMessageMapper.findPendingTimeout(timeout);
        if (pendingMessages.isEmpty()) {
            return;
        }
        log.info("[MessageRelay] 扫描到 {} 条超时 PENDING 消息，开始重试", pendingMessages.size());

        for (LocalMessage msg : pendingMessages) {
            try {
                if (msg.getRetryCount() >= 5) {
                    // 超过重试上限，标记为 DEAD，告警
                    updateStatus(msg.getToken(), MessageStatus.DEAD, null);
                    log.error("[MessageRelay] 消息重试超限，已标记为 DEAD，需人工介入！" +
                                    "token={}，voucherId={}，userId={}",
                            msg.getToken(), msg.getVoucherId(), msg.getUserId());
                    continue;
                }

                // 重新投递
                SeckillMessage message = new SeckillMessage(
                        msg.getUserId(), msg.getVoucherId(), msg.getToken());
                String json = objectMapper.writeValueAsString(message);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                        RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                        json
                );
                // 投递成功，更新状态=SENT
                updateStatus(msg.getToken(), MessageStatus.SENT, null);
                log.info("[MessageRelay] 重试投递成功，token={}，retryCount={}",
                        msg.getToken(), msg.getRetryCount() + 1);

            } catch (Exception e) {
                // 重试失败，retryCount+1，保持 PENDING
                incrementRetryCount(msg.getId());
                log.warn("[MessageRelay] 重试投递失败，token={}，retryCount={}，原因：{}",
                        msg.getToken(), msg.getRetryCount() + 1, e.getMessage());
            }
        }
    }

    /**
     * 更新消息状态
     */
    private void updateStatus(String token, MessageStatus status, Integer retryCount) {
        LambdaUpdateWrapper<LocalMessage> wrapper = new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getToken, token)
                .set(LocalMessage::getStatus, status.name());
        if (retryCount != null) {
            wrapper.set(LocalMessage::getRetryCount, retryCount);
        }
        localMessageMapper.update(null, wrapper);
    }

    /**
     * 重试次数 +1
     */
    private void incrementRetryCount(Long id) {
        localMessageMapper.update(null,
                new LambdaUpdateWrapper<LocalMessage>()
                        .eq(LocalMessage::getId, id)
                        .setSql("retry_count = retry_count + 1"));
    }
}
