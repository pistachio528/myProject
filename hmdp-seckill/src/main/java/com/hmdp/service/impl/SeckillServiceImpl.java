package com.hmdp.service.impl;

import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.dto.LimitResult;
import com.hmdp.dto.SeckillMessage;
import com.hmdp.dto.SeckillResult;
import com.hmdp.dto.StockResult;
import com.hmdp.dto.VoucherActivity;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.enums.LimitType;
import com.hmdp.enums.SeckillResultCode;
import com.hmdp.enums.TokenStatus;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.IdempotencyGuard;
import com.hmdp.service.MessageRelay;
import com.hmdp.service.SeckillService;
import com.hmdp.service.StockManager;
import com.hmdp.service.VoucherService;
import com.hmdp.strategy.LimitStrategy;
import com.hmdp.strategy.LimitStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀主链路服务实现
 * <p>
 * 主链路顺序（全 Redis 化，不触碰 DB 写操作）：
 * 1. IdempotencyGuard.exists → 幂等前置检查
 * 2. 从 DB 查 VoucherActivity（limitType、maxCount）
 * 3. LimitStrategyFactory.getStrategy(limitType).check → 限购校验
 * 4. StockManager.deductStock → Lua 原子扣减（时间窗口 + 库存 + 幂等）
 * 5. 生成 UUID token
 * 6. @Transactional 块：MessageRelay.saveMessage（写 local_message）
 * 7. Redis SET seckill:order:status:{token} = QUEUING，TTL=5min
 * 8. MessageRelay.relay（发 MQ）
 * 9. 返回 SeckillResult.success(token)
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final IdempotencyGuard idempotencyGuard;
    private final LimitStrategyFactory limitStrategyFactory;
    private final StockManager stockManager;
    private final MessageRelay messageRelay;
    private final MessageTransactionService messageTransactionService;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final VoucherService voucherService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public SeckillResult doSeckill(Long userId, Long voucherId) {
        // ① 幂等前置检查（快速失败，避免后续无效操作）
        if (idempotencyGuard.exists(userId, voucherId)) {
            log.debug("[SeckillService] 重复下单，userId={}，voucherId={}", userId, voucherId);
            return SeckillResult.fail(SeckillResultCode.DUPLICATE_ORDER);
        }

        // ② 从 DB 查询活动信息（limitType、maxCount），带缓存穿透防护
        SeckillVoucher voucher = voucherService.getByIdWithCacheProtection(voucherId);
        if (voucher == null) {
            return SeckillResult.fail(SeckillResultCode.ACTIVITY_NOT_FOUND);
        }
        VoucherActivity activity = buildActivity(voucher);

        // ③ 限购策略校验
        LimitStrategy limitStrategy = limitStrategyFactory.getStrategy(activity.getLimitType());
        LimitResult limitResult = limitStrategy.check(userId, voucherId, activity);
        if (!limitResult.isPassed()) {
            log.debug("[SeckillService] 限购校验不通过，userId={}，voucherId={}，code={}",
                    userId, voucherId, limitResult.getCode());
            return SeckillResult.fail(limitResult.getCode());
        }

        // ④ Lua 原子扣减（时间窗口 + 库存 + 幂等 SET NX）
        long currentTimeMillis = System.currentTimeMillis();
        StockResult stockResult = stockManager.deductStock(voucherId, userId, currentTimeMillis);
        if (!stockResult.isSuccess()) {
            log.debug("[SeckillService] 库存扣减失败，userId={}，voucherId={}，code={}",
                    userId, voucherId, stockResult.getCode());
            return SeckillResult.fail(stockResult.getCode());
        }

        // ⑤ 生成 UUID token
        String token = UUID.randomUUID().toString();
        SeckillMessage message = new SeckillMessage(userId, voucherId, token);

        // ⑥ 事务块：写本地消息表（委托给独立 Bean，避免 self-invocation 导致事务失效）
        messageTransactionService.saveMessageInTransaction(message);

        // ⑦ 写 Redis token 状态 = QUEUING，TTL=5min
        stringRedisTemplate.opsForValue().set(
                SeckillRedisKey.orderStatus(token),
                TokenStatus.QUEUING.name(),
                5, TimeUnit.MINUTES
        );

        // ⑧ 发送 MQ 消息（异步下单，在事务提交后执行）
        messageRelay.relay(message);

        log.info("[SeckillService] 秒杀成功，userId={}，voucherId={}，token={}", userId, voucherId, token);
        return SeckillResult.success(token);
    }

    /**
     * 将 SeckillVoucher 转换为 VoucherActivity DTO
     */
    private VoucherActivity buildActivity(SeckillVoucher voucher) {
        VoucherActivity activity = new VoucherActivity();
        activity.setVoucherId(voucher.getId());
        activity.setStartTime(voucher.getBeginTime());
        activity.setEndTime(voucher.getEndTime());

        // 读取 limitType 字段（若 DB 字段不存在则默认 ONE_PER_USER）
        LimitType limitType = LimitType.ONE_PER_USER;
        if (voucher.getLimitType() != null) {
            try {
                limitType = LimitType.valueOf(voucher.getLimitType());
            } catch (IllegalArgumentException e) {
                log.warn("[SeckillService] 未知 limitType={}，使用默认 ONE_PER_USER", voucher.getLimitType());
            }
        }
        activity.setLimitType(limitType);

        Integer maxCount = voucher.getMaxCount();
        activity.setMaxCount(maxCount != null ? maxCount : 1);
        return activity;
    }
}
