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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 一天一单限购策略
 * <p>
 * key = seckill:limit:{voucherId}:{date}  → Set 类型，存当天已购用户 userId
 * SADD 返回 1 = 首次购买（通过），返回 0 = 当天已购（拒绝）。
 * TTL = 当天剩余秒数（到 23:59:59），自然日结束后 Set 自动过期，次日可重新购买。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OneDailyOrderStrategy implements LimitStrategy {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public LimitResult check(Long userId, Long voucherId, VoucherActivity activity) {
        // 构建含日期的 key，每天一个 Set
        String date = LocalDate.now().format(DATE_FORMATTER);
        String key  = SeckillRedisKey.limitDaily(voucherId, date);

        // SADD：返回 1=首次加入（通过），返回 0=已存在（当天已购，拒绝）
        Long added = stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));

        if (Long.valueOf(1L).equals(added)) {
            // 首次写入时设置 TTL（到当天 23:59:59）
            LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
            long ttlSeconds = java.time.Duration.between(LocalDateTime.now(), endOfDay).getSeconds();
            if (ttlSeconds <= 0) ttlSeconds = 1;
            stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            return LimitResult.pass();
        }
        return LimitResult.reject(SeckillResultCode.DUPLICATE_ORDER);
    }

    @Override
    public LimitType getType() {
        return LimitType.ONE_PER_DAY;
    }
}
