package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 秒杀优惠券 Mapper
 */
@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /**
     * 原子扣减数据库库存（stock >= 1 时才执行，防止超卖）
     * 返回影响行数：1=成功，0=库存不足
     */
    @Update("UPDATE seckill_voucher SET stock = stock - 1 WHERE id = #{voucherId} AND stock >= 1")
    int decrementStock(@Param("voucherId") Long voucherId);
}
