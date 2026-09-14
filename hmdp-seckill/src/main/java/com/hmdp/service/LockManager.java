package com.hmdp.service;

/**
 * 分布式锁管理器接口
 * <p>
 * 基于 Redisson RLock 实现，内置 watchdog 自动续期，防止锁永久占用和误删。
 * </p>
 */
public interface LockManager {

    /**
     * 尝试获取分布式锁
     * <p>
     * waitTime=500ms，leaseTime=10s（watchdog 会自动续期）
     * </p>
     *
     * @param lockKey 锁 key
     * @return true=获取成功，false=超时未获取到
     */
    boolean tryLock(String lockKey);

    /**
     * 释放分布式锁
     * <p>
     * 内部判断 isHeldByCurrentThread() 后再释放，防止误删他人的锁。
     * 建议在 finally 块中调用。
     * </p>
     *
     * @param lockKey 锁 key
     */
    void unlock(String lockKey);
}
