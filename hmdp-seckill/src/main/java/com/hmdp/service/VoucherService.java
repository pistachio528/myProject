package com.hmdp.service;

import com.hmdp.entity.SeckillVoucher;

import java.util.List;

/**
 * 优惠券服务接口
 */
public interface VoucherService {

    /**
     * 按店铺 ID 查询秒杀券列表
     *
     * @param shopId 店铺 ID（预留字段，当前版本返回全部）
     * @return 秒杀券列表
     */
    List<SeckillVoucher> listByShopId(Long shopId);

    /**
     * 按 voucherId 查询单个优惠券，带缓存穿透防护
     * <p>
     * 防护策略：布隆过滤器（第一道）+ 缓存空值（第二道）
     * </p>
     *
     * @param voucherId 优惠券 ID
     * @return 优惠券实体，不存在时返回 null
     */
    SeckillVoucher getByIdWithCacheProtection(Long voucherId);
}
