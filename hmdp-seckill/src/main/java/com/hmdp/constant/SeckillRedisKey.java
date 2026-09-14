package com.hmdp.constant;

/**
 * 秒杀模块 Redis Key 常量类
 * <p>
 * 所有 Redis Key 统一在此定义，避免魔法字符串散落各处，便于维护和排查问题。
 * Key 命名规范：{业务域}:{子域}:{标识符}
 * </p>
 */
public class SeckillRedisKey {

    private SeckillRedisKey() {
        // 工具类，禁止实例化
    }

    // ==================== Key 模板常量 ====================

    /** 库存 key 前缀：seckill:stock:{voucherId} → String 类型，原子计数 */
    public static final String STOCK = "seckill:stock:";

    /** 活动信息 key 前缀：seckill:activity:{voucherId} → Hash 类型，逻辑过期 */
    public static final String ACTIVITY = "seckill:activity:";

    /** 分布式锁 key 前缀：seckill:lock:{userId}:{voucherId} → Redisson RLock */
    public static final String LOCK = "seckill:lock:";

    /** 幂等标记 key 前缀：seckill:idempotent:{userId}:{voucherId} → String，TTL=24h */
    public static final String IDEMPOTENT = "seckill:idempotent:";

    /** 已购用户集合 key 前缀：seckill:buyers:{voucherId} → Set 类型，存已购 userId */
    public static final String BUYERS = "seckill:buyers:";

    /** 一天一单限购 key 前缀：seckill:limit:{voucherId}:{date} → Set 类型，存当天已购 userId */
    public static final String LIMIT_DAILY = "seckill:limit:";

    /** 一人N单计数 key 前缀：seckill:limit:count:{userId}:{voucherId} → String，INCR 计数 */
    public static final String LIMIT_COUNT = "seckill:limit:count:";

    /** 排队 token 状态 key 前缀：seckill:order:status:{token} → String，TTL=5min */
    public static final String ORDER_STATUS = "seckill:order:status:";

    /** 对账分布式锁 key 前缀：reconciliation:lock:{voucherId} → Redisson RLock */
    public static final String RECONCILIATION_LOCK = "reconciliation:lock:";

    /** 空值缓存 key 前缀：seckill:null:voucher:{voucherId} → String，TTL=2min，防缓存穿透 */
    public static final String NULL_VOUCHER = "seckill:null:voucher:";

    // ==================== 格式化方法 ====================

    /**
     * 库存 key
     * 示例：seckill:stock:1001
     */
    public static String stock(Long voucherId) {
        return STOCK + voucherId;
    }

    /**
     * 活动信息 key
     * 示例：seckill:activity:1001
     */
    public static String activity(Long voucherId) {
        return ACTIVITY + voucherId;
    }

    /**
     * 一人一单分布式锁 key
     * 示例：seckill:lock:2001:1001
     */
    public static String lock(Long userId, Long voucherId) {
        return LOCK + userId + ":" + voucherId;
    }

    /**
     * 幂等标记 key（同时也是 Lua 脚本中的幂等 key）
     * 示例：seckill:idempotent:2001:1001
     */
    public static String idempotent(Long userId, Long voucherId) {
        return IDEMPOTENT + userId + ":" + voucherId;
    }

    /**
     * 已购用户集合 key（一人一单）
     * 示例：seckill:buyers:1001
     */
    public static String buyers(Long voucherId) {
        return BUYERS + voucherId;
    }

    /**
     * 一天一单限购 key（含日期，TTL=当天剩余秒数）
     * 示例：seckill:limit:1001:2024-01-15
     *
     * @param date 日期字符串，格式 yyyy-MM-dd
     */
    public static String limitDaily(Long voucherId, String date) {
        return LIMIT_DAILY + voucherId + ":" + date;
    }

    /**
     * 一人N单计数 key
     * 示例：seckill:limit:count:2001:1001
     */
    public static String limitCount(Long userId, Long voucherId) {
        return LIMIT_COUNT + userId + ":" + voucherId;
    }

    /**
     * 排队 token 状态 key
     * 示例：seckill:order:status:550e8400-e29b-41d4-a716-446655440000
     */
    public static String orderStatus(String token) {
        return ORDER_STATUS + token;
    }

    /**
     * 对账分布式锁 key
     * 示例：reconciliation:lock:1001
     */
    public static String reconciliationLock(Long voucherId) {
        return RECONCILIATION_LOCK + voucherId;
    }

    /**
     * 空值缓存 key（防缓存穿透）
     * 示例：seckill:null:voucher:9999
     */
    public static String nullVoucher(Long voucherId) {
        return NULL_VOUCHER + voucherId;
    }
}
