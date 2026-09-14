package com.hmdp.controller;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券控制器
 */
@RestController
@RequestMapping("/api/voucher")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    /**
     * 按店铺查询秒杀券列表
     * GET /api/voucher/list/{shopId}
     */
    @GetMapping("/list/{shopId}")
    public List<SeckillVoucher> listByShopId(@PathVariable Long shopId) {
        return voucherService.listByShopId(shopId);
    }
}
