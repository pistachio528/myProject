package com.hmdp.service.impl;

import com.hmdp.service.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁管理器实现（基于 Redisson RLock）
 * <p>
 * 使用 ThreadLocal 存储当前线程持有的锁引用，
 * 保证 unlock 时能正确判断 isHeldByCurrentThread()，防止误删他人的锁。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LockManagerImpl implements LockManager {

    private final RedissonClient redissonClient;

    /**
     * ThreadLocal 存储当前线程持有的锁引用
     * key = lockKey，value = RLock 实例
     */
    private static final ThreadLocal<Map<String, RLock>> LOCK_HOLDER =
            ThreadLocal.withInitial(HashMap::new);

    /**
     * 尝试获取分布式锁
     * waitTime=500ms（等待时间），leaseTime=10s（持有时间，watchdog 会自动续期）
     *
     * @param lockKey 锁 key
     * @return true=获取成功，false=超时未获取到
     */
    @Override
    public boolean tryLock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = lock.tryLock(500, 10000, TimeUnit.MILLISECONDS);
            if (acquired) {
                // 存入 ThreadLocal，供 unlock 使用
                LOCK_HOLDER.get().put(lockKey, lock);
                log.debug("[LockManager] 获取锁成功，key={}", lockKey);
            } else {
                log.warn("[LockManager] 获取锁超时，key={}", lockKey);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[LockManager] 获取锁被中断，key={}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     * 先判断 isHeldByCurrentThread()，防止误删他人的锁
     *
     * @param lockKey 锁 key
     */
    @Override
    public void unlock(String lockKey) {
        Map<String, RLock> lockMap = LOCK_HOLDER.get();
        RLock lock = lockMap.get(lockKey);
        if (lock == null) {
            // 从 Redisson 重新获取引用（兜底）
            lock = redissonClient.getLock(lockKey);
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[LockManager] 释放锁成功，key={}", lockKey);
            } else {
                log.warn("[LockManager] 当前线程未持有锁，跳过释放，key={}", lockKey);
            }
        } catch (Exception e) {
            log.error("[LockManager] 释放锁异常，key={}，原因：{}", lockKey, e.getMessage(), e);
        } finally {
            // 清理 ThreadLocal，防止内存泄漏
            lockMap.remove(lockKey);
            if (lockMap.isEmpty()) {
                LOCK_HOLDER.remove();
            }
        }
    }
}
