package com.hmdp.service.impl;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.dto.SeckillMessage;
import com.hmdp.dto.StockResult;
import com.hmdp.dto.VoucherActivity;
import com.hmdp.entity.LocalMessage;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.enums.MessageStatus;
import com.hmdp.enums.SeckillResultCode;
import com.hmdp.enums.TokenStatus;
import com.hmdp.mapper.LocalMessageMapper;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.StockManager;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 库存管理器实现
 * <p>
 * 核心职责：
 * 1. 通过 Lua 脚本原子扣减库存（含时间窗口 + 幂等校验）
 * 2. 服务启动时预热所有有效活动的库存和活动信息到 Redis
 * 3. 死信消费者：消费失败消息，回补库存，更新 token 状态
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockManagerImpl implements StockManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final LocalMessageMapper localMessageMapper;

    /** Lua 脚本对象（@PostConstruct 加载，避免每次请求重新解析） */
    private DefaultRedisScript<Long> seckillScript;

    /**
     * 服务启动时加载 Lua 脚本
     */
    @PostConstruct
    public void initScript() {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/seckill_deduct.lua")));
        seckillScript.setResultType(Long.class);
        log.info("[StockManager] Lua 脚本加载完成");
    }

    /**
     * 原子扣减库存（Lua 脚本执行）
     * <p>
     * 当 Lua 返回 code=5（库存 key 不存在）时，自动从 DB 重建缓存并重试一次。
     * </p>
     *
     * @param voucherId          优惠券ID
     * @param userId             用户ID（用于幂等 key）
     * @param currentTimeMillis  当前时间戳（毫秒）
     * @return 操作结果码
     */
    @Override
    public StockResult deductStock(Long voucherId, Long userId, long currentTimeMillis) {
        // 构建两个 KEYS
        String activityKey = SeckillRedisKey.activity(voucherId);
        String stockKey    = SeckillRedisKey.stock(voucherId);

        // 执行 Lua 脚本，ARGV[1]=当前时间戳
        Long result = stringRedisTemplate.execute(
                seckillScript,
                Arrays.asList(activityKey, stockKey),
                String.valueOf(currentTimeMillis)
        );

        int code = (result == null) ? SeckillResultCode.SYSTEM_BUSY.getCode() : result.intValue();

        // 库存 key 不存在 或 库存为负数 → 从 DB 重建缓存并重试一次
        if (code == SeckillResultCode.STOCK_CACHE_MISS.getCode()
                || code == SeckillResultCode.STOCK_NEGATIVE.getCode()) {
            log.warn("[StockManager] 库存缓存异常(code={})，触发重建，voucherId={}", code, voucherId);
            preheatStock(voucherId);

            // 重试一次 Lua 脚本
            result = stringRedisTemplate.execute(
                    seckillScript,
                    Arrays.asList(activityKey, stockKey),
                    String.valueOf(currentTimeMillis)
            );
            code = (result == null) ? SeckillResultCode.SYSTEM_BUSY.getCode() : result.intValue();

            // 如果重建后仍然异常，说明 DB 中也没有该活动
            if (code == SeckillResultCode.STOCK_CACHE_MISS.getCode()
                    || code == SeckillResultCode.STOCK_NEGATIVE.getCode()) {
                log.error("[StockManager] 重建后仍异常，voucherId={}", voucherId);
                return StockResult.of(SeckillResultCode.ACTIVITY_NOT_FOUND);
            }
        }

        return StockResult.of(SeckillResultCode.fromCode(code));
    }

    /**
     * 原子回补库存（INCR）
     * 死信消费者调用，将 Redis 库存加 1
     */
    @Override
    public boolean replenishStock(Long voucherId) {
        try {
            stringRedisTemplate.opsForValue().increment(SeckillRedisKey.stock(voucherId));
            log.info("[StockManager] 库存回补成功，voucherId={}", voucherId);
            return true;
        } catch (Exception e) {
            log.error("[StockManager] 库存回补失败，voucherId={}，原因：{}", voucherId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 预热单个活动的库存和活动信息到 Redis
     * 从 DB 查询 SeckillVoucher，写入 stock（String）和 activity（Hash）
     */
    @Override
    public void preheatStock(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            log.warn("[StockManager] 预热失败，活动不存在，voucherId={}", voucherId);
            return;
        }

        String stockKey    = SeckillRedisKey.stock(voucherId);
        String activityKey = SeckillRedisKey.activity(voucherId);

        // 写入库存，EXPIREAT 设置为活动结束时间
        long endEpochMilli = voucher.getEndTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long startMillis = voucher.getBeginTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long endMillis = endEpochMilli;
        long nowMillis = System.currentTimeMillis();
        log.info("[StockManager] 预热时间戳校验 voucherId={}，now={}，startMillis={}，endMillis={}，endDate={}",
                voucherId, nowMillis, startMillis, endMillis, new java.util.Date(endEpochMilli));

        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
        if (endEpochMilli > nowMillis) {
            stringRedisTemplate.expireAt(stockKey, new java.util.Date(endEpochMilli));
        } else {
            log.error("[StockManager] endTime 已过期，跳过 expireAt 设置，voucherId={}，endMillis={}", voucherId, endEpochMilli);
        }
        stringRedisTemplate.opsForHash().put(activityKey, "startTime", String.valueOf(startMillis));
        stringRedisTemplate.opsForHash().put(activityKey, "endTime",   String.valueOf(endMillis));
        // logicExpireTime 设为 endTime（逻辑过期策略）
        stringRedisTemplate.opsForHash().put(activityKey, "logicExpireTime", String.valueOf(endMillis));

        log.info("[StockManager] 活动预热完成，voucherId={}，stock={}，startTime={}，endTime={}",
                voucherId, voucher.getStock(), voucher.getBeginTime(), voucher.getEndTime());
    }

    /**
     * 缓存活动信息到 Redis（供外部调用，如活动变更时主动刷新）
     */
    @Override
    public void cacheActivity(VoucherActivity activity) {
        String activityKey = SeckillRedisKey.activity(activity.getVoucherId());
        long startMillis = activity.getStartTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long endMillis = activity.getEndTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        stringRedisTemplate.opsForHash().put(activityKey, "startTime",       String.valueOf(startMillis));
        stringRedisTemplate.opsForHash().put(activityKey, "endTime",         String.valueOf(endMillis));
        stringRedisTemplate.opsForHash().put(activityKey, "logicExpireTime", String.valueOf(endMillis));
        log.info("[StockManager] 活动缓存刷新，voucherId={}", activity.getVoucherId());
    }

    /**
     * 从 DB 重新查询活动信息并刷新 Redis 缓存
     */
    @Override
    public void refreshActivity(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            log.warn("[StockManager] refreshActivity 失败，活动不存在，voucherId={}", voucherId);
            return;
        }
        VoucherActivity activity = new VoucherActivity();
        activity.setVoucherId(voucherId);
        activity.setStartTime(voucher.getBeginTime());
        activity.setEndTime(voucher.getEndTime());
        cacheActivity(activity);
    }

    /**
     * ApplicationRunner 方法已移至 SeckillPreheatRunner，此处保留空实现以兼容旧引用
     * @deprecated 请使用 SeckillPreheatRunner
     */
    @Deprecated
    public void runPreheat() {
        // 预热逻辑已移至 SeckillPreheatRunner
    }

    /**
     * 死信队列消费者：消费失败消息，回补库存，更新 token 状态为 FAILED
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_DLQ)
    public void consumeDeadLetter(String messageJson, Channel channel, Message rawMessage) {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        log.warn("[StockManager] 收到死信消息：{}", messageJson);
        try {
            // 解析消息
            SeckillMessage msg = parseMessage(messageJson);
            if (msg == null) {
                log.error("[StockManager] 死信消息解析失败，消息内容：{}", messageJson);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 回补库存
            boolean replenished = replenishStock(msg.getVoucherId());
            if (!replenished) {
                log.error("[StockManager] 库存回补失败，需人工介入，voucherId={}", msg.getVoucherId());
            }

            // 更新本地消息表状态为 REPLENISHED
            updateLocalMessageStatus(msg.getToken(), MessageStatus.REPLENISHED);

            // 更新 Redis token 状态为 FAILED，TTL=5min
            String tokenKey = SeckillRedisKey.orderStatus(msg.getToken());
            stringRedisTemplate.opsForValue().set(
                    tokenKey, TokenStatus.FAILED.name(), 5, TimeUnit.MINUTES);

            channel.basicAck(deliveryTag, false);
            log.info("[StockManager] 死信处理完成，token={}，voucherId={}", msg.getToken(), msg.getVoucherId());
        } catch (Exception e) {
            log.error("[StockManager] 死信消费异常，消息：{}，原因：{}", messageJson, e.getMessage(), e);
            try {
                // 不重新入队，避免无限循环
                channel.basicAck(deliveryTag, false);
            } catch (Exception ex) {
                log.error("[StockManager] ACK 失败", ex);
            }
        }
    }

    /**
     * 解析 JSON 消息为 SeckillMessage
     */
    private SeckillMessage parseMessage(String json) {
        try {
            // 使用 Jackson（Spring Boot 默认）解析
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, SeckillMessage.class);
        } catch (Exception e) {
            log.error("[StockManager] 消息解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新本地消息表状态（按 token 查找）
     */
    private void updateLocalMessageStatus(String token, MessageStatus status) {
        try {
            LocalMessage update = new LocalMessage();
            update.setStatus(status.name());
            localMessageMapper.update(update,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LocalMessage>()
                            .eq(LocalMessage::getToken, token));
        } catch (Exception e) {
            log.error("[StockManager] 更新本地消息状态失败，token={}，status={}，原因：{}",
                    token, status, e.getMessage(), e);
        }
    }
}
