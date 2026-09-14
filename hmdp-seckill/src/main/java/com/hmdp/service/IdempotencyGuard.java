package com.hmdp.service;

/**
 * 幂等守卫接口
 * <p>
 * 防止重复下单的第一道防线（Redis SET NX），
 * 数据库唯一索引为第二道防线。
 * </p>
 */
public interface IdempotencyGuard {

    /**
     * 检查幂等标记是否存在
     *
     * @param userId    用户ID
     * @param voucherId 优惠券ID
     * @return true=已存在（重复请求），false=不存在（首次请求）
     */
    boolean exists(Long userId, Long voucherId);

    /**
     * 写入幂等标记（SET NX，TTL=24h）
     *
     * @param userId    用户ID
     * @param voucherId 优惠券ID
     * @return true=写入成功（首次），false=已存在（重复）
     */
    boolean mark(Long userId, Long voucherId);
}
