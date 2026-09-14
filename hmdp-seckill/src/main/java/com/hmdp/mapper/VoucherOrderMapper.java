package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀订单 Mapper
 */
@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
}
