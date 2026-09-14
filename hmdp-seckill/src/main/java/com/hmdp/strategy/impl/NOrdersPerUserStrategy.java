package com.hmdp.strategy.impl;

import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.dto.LimitResult;
import com.hmdp.dto.VoucherActivity;
import com.hmdp.enums.LimitType;
import com.hmdp.enums.SeckillResultCode;
import com.hmdp.strategy.LimitStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 一人N单限购策略
 * <p>
 * 使用 INCR 计数：
 * - 计数结果 <= maxCount：通过
 * - 计数结果 > maxCount：DECR 回滚，返回 LIMIT_EXCEEDED
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NOrdersPerUserStrategy implements LimitStrategy {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public LimitResult check(Long userId, Long voucherId, VoucherActivity activity) {
        String key = SeckillRedisKey.limitCount(userId, voucherId);

        // INCR 计数
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            log.error("[NOrdersPerUserStrategy] INCR 返回 null，key={}", key);
            return LimitResult.reject(SeckillResultCode.SYSTEM_BUSY);
        }

        int maxCount = (activity.getMaxCount() != null) ? activity.getMaxCount() : 1;

        if (count > maxCount) {
            // 超出限购次数，DECR 回滚，避免计数虚高
            stringRedisTemplate.opsForValue().decrement(key);
            return LimitResult.reject(SeckillResultCode.LIMIT_EXCEEDED);
        }

        return LimitResult.pass();
    }

    @Override
    public LimitType getType() {
        return LimitType.N_PER_USER;
    }
}
