package com.hmdp.service;

import com.hmdp.dto.SeckillResult;

/**
 * 秒杀主链路服务接口
 */
public interface SeckillService {

    /**
     * 秒杀主入口
     * <p>
     * 主链路：幂等检查 → 限购校验 → Lua 原子扣减 → 生成 token → 写本地消息表 → 发 MQ → 返回 token
     * 全程不触碰数据库写操作，RT ≤ 50ms。
     * </p>
     *
     * @param userId    用户ID
     * @param voucherId 优惠券ID
     * @return 秒杀结果（含 token 或错误码）
     */
    SeckillResult doSeckill(Long userId, Long voucherId);
}
