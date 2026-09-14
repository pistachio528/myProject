package com.hmdp.enums;

import lombok.Getter;

/**
 * 秒杀结果码枚举
 * <p>
 * 与 Lua 脚本返回值一一对应（0-5），6-8 为 Java 层业务码。
 * 统一错误码体系，便于前端处理和日志排查。
 * </p>
 */
@Getter
public enum SeckillResultCode {

    /** 0 - 秒杀成功 */
    SUCCESS(0, "秒杀成功"),

    /** 1 - 活动不存在（Redis 中无活动信息） */
    ACTIVITY_NOT_FOUND(1, "活动不存在"),

    /** 2 - 活动未开始 */
    ACTIVITY_NOT_STARTED(2, "活动未开始"),

    /** 3 - 活动已结束 */
    ACTIVITY_ENDED(3, "活动已结束"),

    /** 4 - 库存不足 */
    STOCK_INSUFFICIENT(4, "库存不足"),

    /** 5 - 库存缓存丢失（key 不存在，需从 DB 重建） */
    STOCK_CACHE_MISS(5, "库存缓存重建中，请稍后重试"),

    /** 6 - 库存为负数（数据异常，需从 DB 重建） */
    STOCK_NEGATIVE(6, "库存数据异常，正在修复"),

    /** 7 - 超出限购次数（LimitStrategy 返回） */
    LIMIT_EXCEEDED(7, "超出限购次数"),

    /** 8 - 系统繁忙（分布式锁获取超时） */
    SYSTEM_BUSY(8, "系统繁忙，请稍后重试"),

    /** 9 - 查询超时（token 对应的 Redis key 已过期） */
    QUERY_TIMEOUT(9, "查询超时，请前往订单列表确认"),

    /** 10 - 重复下单（幂等 key 已存在） */
    DUPLICATE_ORDER(10, "您已参与过该活动");

    /** 错误码（与 Lua 脚本返回值对应） */
    private final int code;

    /** 错误描述 */
    private final String message;

    SeckillResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据 Lua 脚本返回的整数码查找对应枚举
     *
     * @param code Lua 返回值
     * @return 对应枚举，未匹配时返回 SYSTEM_BUSY
     */
    public static SeckillResultCode fromCode(int code) {
        for (SeckillResultCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return SYSTEM_BUSY;
    }
}
