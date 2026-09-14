package com.hmdp.strategy;

import com.hmdp.dto.LimitResult;
import com.hmdp.dto.VoucherActivity;
import com.hmdp.enums.LimitType;

/**
 * 限购策略接口（策略模式）
 * <p>
 * 每种限购规则对应一个实现类，通过 LimitStrategyFactory 路由。
 * 新增限购规则只需新增实现类并注册为 Spring Bean，无需修改 SeckillService。
 * </p>
 */
public interface LimitStrategy {

    /**
     * 校验用户是否满足限购规则
     *
     * @param userId   用户ID
     * @param voucherId 优惠券ID
     * @param activity 活动信息（含限购配置）
     * @return 校验结果
     */
    LimitResult check(Long userId, Long voucherId, VoucherActivity activity);

    /**
     * 策略类型标识，用于 LimitStrategyFactory 路由
     */
    LimitType getType();
}
