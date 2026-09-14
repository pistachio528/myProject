package com.hmdp.strategy;

import com.hmdp.enums.LimitType;
import com.hmdp.strategy.impl.OneOrderPerUserStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 限购策略工厂
 * <p>
 * 通过 @Autowired List<LimitStrategy> 自动收集所有策略实现类，
 * 以 getType() 为 key 构建路由 Map。
 * 新增策略只需新增实现类并注册为 Bean，无需修改此工厂。
 * </p>
 */
@Slf4j
@Component
public class LimitStrategyFactory {

    /** 策略路由 Map：LimitType → LimitStrategy */
    private final Map<LimitType, LimitStrategy> strategyMap = new HashMap<>();

    /** 默认策略（一人一单） */
    private final OneOrderPerUserStrategy defaultStrategy;

    /**
     * Spring 自动注入所有 LimitStrategy 实现，构建路由 Map
     */
    @Autowired
    public LimitStrategyFactory(List<LimitStrategy> strategies,
                                OneOrderPerUserStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
        for (LimitStrategy strategy : strategies) {
            strategyMap.put(strategy.getType(), strategy);
            log.info("[LimitStrategyFactory] 注册限购策略：{}", strategy.getType());
        }
    }

    /**
     * 根据限购类型获取对应策略
     * 未找到时返回默认策略（一人一单）
     *
     * @param limitType 限购类型
     * @return 对应的限购策略实现
     */
    public LimitStrategy getStrategy(LimitType limitType) {
        if (limitType == null) {
            return defaultStrategy;
        }
        LimitStrategy strategy = strategyMap.get(limitType);
        if (strategy == null) {
            log.warn("[LimitStrategyFactory] 未找到策略 {}，使用默认策略 ONE_PER_USER", limitType);
            return defaultStrategy;
        }
        return strategy;
    }
}
