package com.hmdp.ai.service;

/**
 * 限流服务接口
 * <p>
 * 两层限流：
 * 1. 用户级滑动窗口限流：单用户在时间窗口内的最大请求数
 * 2. 全局并发限流：同时处理的 LLM 请求总数上限
 */
public interface RateLimiter {

    /**
     * 检查用户级限流
     *
     * @param userId 用户ID
     * @return true=允许通过，false=触发限流
     */
    boolean allowUser(Long userId);

    /**
     * 尝试获取全局并发槽位
     *
     * @return true=获取成功，false=并发已满
     */
    boolean tryAcquireGlobal();

    /**
     * 释放全局并发槽位（处理完成后调用）
     *
     * @param userId 用户ID（用于日志）
     */
    void releaseGlobal(Long userId);

    /**
     * 获取用户当前窗口内的剩余可用次数
     *
     * @param userId 用户ID
     * @return 剩余次数
     */
    int remainingQuota(Long userId);

    /**
     * 获取当前全局并发数
     *
     * @return 当前并发数
     */
    int currentConcurrency();
}
