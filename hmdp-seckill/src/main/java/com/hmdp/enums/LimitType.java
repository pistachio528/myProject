package com.hmdp.enums;

/**
 * 限购类型枚举
 * <p>
 * 对应三种限购策略实现类，由 LimitStrategyFactory 路由。
 * </p>
 */
public enum LimitType {

    /** 一人一单：同一用户对同一活动仅限购 1 次 */
    ONE_PER_USER,

    /** 一天一单：同一用户同一活动每自然日仅限购 1 次 */
    ONE_PER_DAY,

    /** 一人N单：同一用户对同一活动最多限购 N 次，N 由活动配置决定 */
    N_PER_USER
}
