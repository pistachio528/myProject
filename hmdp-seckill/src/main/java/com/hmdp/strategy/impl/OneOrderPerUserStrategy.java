package com.hmdp.strategy.impl;

import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.dto.LimitResult;
import com.hmdp.dto.VoucherActivity;
import com.hmdp.enums.LimitType;
import com.hmdp.enums.SeckillResultCode;
import com.hmdp.strategy.LimitStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 一人一单限购策略
 * <p>
 * key = seckill:buyers:{voucherId}  → Set 类型，存所有已购用户 userId
 * SADD 返回 1 = 首次购买（通过），返回 0 = 已存在（已购，拒绝）。
 * 无 TTL，永久限购。
 * 服务启动时从数据库预热已购用户到此 Set。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OneOrderPerUserStrategy implements LimitStrategy {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public LimitResult check(Long userId, Long voucherId, VoucherActivity activity) {
        String key = SeckillRedisKey.buyers(voucherId);

        // SADD：返回 1=首次加入（通过），返回 0=已存在（已购，拒绝）
        Long added = stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));

        if (Long.valueOf(1L).equals(added)) {
            return LimitResult.pass();
        }
        return LimitResult.reject(SeckillResultCode.DUPLICATE_ORDER);
    }

    @Override
    public LimitType getType() {
        return LimitType.ONE_PER_USER;
    }
}
