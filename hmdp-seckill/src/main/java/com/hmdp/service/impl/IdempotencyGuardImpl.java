package com.hmdp.service.impl;

import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.service.IdempotencyGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 幂等守卫实现
 * <p>
 * 基于 Redis SET NX 实现，防止重复下单的第一道防线。
 * 第二道防线为数据库唯一索引 uk_user_voucher。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class IdempotencyGuardImpl implements IdempotencyGuard {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 检查幂等标记是否存在
     * key = seckill:idempotent:{userId}:{voucherId}
     */
    @Override
    public boolean exists(Long userId, Long voucherId) {
        String key = SeckillRedisKey.idempotent(userId, voucherId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 写入幂等标记（SET NX，TTL=24h）
     * 返回 true=写入成功（首次），false=已存在（重复）
     */
    @Override
    public boolean mark(Long userId, Long voucherId) {
        String key = SeckillRedisKey.idempotent(userId, voucherId);
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 24, TimeUnit.HOURS);
        return Boolean.TRUE.equals(result);
    }
}
