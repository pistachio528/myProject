package com.hmdp.ai.service.impl;

import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流服务实现
 * <p>
 * 用户级限流：基于 Redis 的滑动窗口计数器
 *   - key: chat:rate:{userId}
 *   - 每次请求 INCR，首次设置 TTL = windowSeconds
 *   - 窗口内计数超过 maxRequestsPerWindow 则拒绝
 * <p>
 * 全局并发限流：JVM 内 AtomicInteger 计数器
 *   - 请求进入时 +1，处理完成后 -1
 *   - 超过 maxConcurrency 则拒绝
 *   - 注意：单节点部署有效；多节点需改用 Redis 分布式计数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterImpl implements RateLimiter {

    private static final String RATE_KEY_PREFIX = "chat:rate:";

    private final StringRedisTemplate redisTemplate;
    private final AiProperties aiProperties;

    /** 全局并发计数器 */
    private final AtomicInteger concurrencyCounter = new AtomicInteger(0);

    @Override
    public boolean allowUser(Long userId) {
        AiProperties.RateLimit cfg = aiProperties.getRateLimit();
        if (!cfg.isEnabled()) return true;

        // 未登录用户（userId=0）单独限流，防止匿名刷接口
        String key = RATE_KEY_PREFIX + (userId != null ? userId : "anonymous");

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return true; // Redis 异常时放行，避免影响正常用户

            if (count == 1) {
                // 首次写入，设置过期时间
                redisTemplate.expire(key, Duration.ofSeconds(cfg.getWindowSeconds()));
            }

            if (count > cfg.getMaxRequestsPerWindow()) {
                log.warn("Rate limit exceeded: userId={}, count={}, limit={}/{}s",
                        userId, count, cfg.getMaxRequestsPerWindow(), cfg.getWindowSeconds());
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis 不可用时降级放行，保证服务可用性
            log.error("Rate limiter Redis error, allowing request: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public boolean tryAcquireGlobal() {
        AiProperties.RateLimit cfg = aiProperties.getRateLimit();
        if (!cfg.isEnabled()) return true;

        int current = concurrencyCounter.incrementAndGet();
        if (current > cfg.getMaxConcurrency()) {
            concurrencyCounter.decrementAndGet();
            log.warn("Global concurrency limit exceeded: current={}, limit={}",
                    current - 1, cfg.getMaxConcurrency());
            return false;
        }
        log.debug("Global concurrency acquired: current={}/{}", current, cfg.getMaxConcurrency());
        return true;
    }

    @Override
    public void releaseGlobal(Long userId) {
        int remaining = concurrencyCounter.decrementAndGet();
        log.debug("Global concurrency released by userId={}: remaining={}", userId, remaining);
    }

    @Override
    public int remainingQuota(Long userId) {
        AiProperties.RateLimit cfg = aiProperties.getRateLimit();
        String key = RATE_KEY_PREFIX + (userId != null ? userId : "anonymous");
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val == null) return cfg.getMaxRequestsPerWindow();
            int used = Integer.parseInt(val);
            return Math.max(0, cfg.getMaxRequestsPerWindow() - used);
        } catch (Exception e) {
            return cfg.getMaxRequestsPerWindow();
        }
    }

    @Override
    public int currentConcurrency() {
        return concurrencyCounter.get();
    }
}
