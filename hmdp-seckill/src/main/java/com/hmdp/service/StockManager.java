package com.hmdp.service;

import com.hmdp.dto.StockResult;
import com.hmdp.dto.VoucherActivity;

/**
 * 库存管理器接口
 * <p>
 * 负责 Redis 库存的原子扣减、回补和预热。
 * 核心操作通过 Lua 脚本保证原子性。
 * </p>
 */
public interface StockManager {

    /**
     * 原子扣减库存（含时间窗口校验）
     * <p>
     * 通过 Lua 脚本在单次 Redis 命令中完成：
     * 活动存在性检查 → 时间窗口校验 → 库存校验 → 幂等 SET NX → 库存 DECRBY 1
     * </p>
     *
     * @param voucherId   优惠券ID
     * @param userId      用户ID（用于幂等 key）
     * @param currentTime 当前时间戳（毫秒）
     * @return 操作结果码
     */
    StockResult deductStock(Long voucherId, Long userId, long currentTime);

    /**
     * 原子回补库存（INCR）
     * <p>
     * 死信消费者调用，将 Redis 库存加 1。
     * </p>
     *
     * @param voucherId 优惠券ID
     * @return 是否回补成功
     */
    boolean replenishStock(Long voucherId);

    /**
     * 服务启动时预热库存至 Redis
     * <p>
     * 从 DB 查询有效活动库存，写入 Redis，EXPIREAT 设置为活动结束时间。
     * </p>
     *
     * @param voucherId 优惠券ID
     */
    void preheatStock(Long voucherId);

    /**
     * 缓存活动信息到 Redis（HSET activity key）
     * <p>
     * 供活动变更时主动刷新缓存。
     * </p>
     *
     * @param activity 活动信息 DTO
     */
    void cacheActivity(VoucherActivity activity);

    /**
     * 从 DB 重新查询活动信息并刷新 Redis 缓存
     *
     * @param voucherId 优惠券ID
     */
    void refreshActivity(Long voucherId);
}
