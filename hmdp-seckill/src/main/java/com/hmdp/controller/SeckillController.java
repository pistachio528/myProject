package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillResult;
import com.hmdp.service.OrderService;
import com.hmdp.service.SeckillService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀控制器
 * <p>
 * POST /api/seckill/{voucherId}：发起秒杀，返回排队 token
 * GET  /api/seckill/order/status/{token}：查询订单进度
 * </p>
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final OrderService orderService;

    /**
     * 发起秒杀
     * 从 ThreadLocal 取当前登录用户 ID（由 LoginInterceptor 注入）
     *
     * @param voucherId 优惠券ID
     * @return 秒杀结果（含 token 或错误信息）
     */
    @PostMapping("/{voucherId}")
    public Result doSeckill(@PathVariable Long voucherId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        SeckillResult result = seckillService.doSeckill(userId, voucherId);
        if (result.isSuccess()) {
            return Result.ok(result);
        }
        return Result.fail(result.getMessage());
    }

    /**
     * 查询当前用户的订单列表（供智能客服调用，通过 userId 参数指定用户）
     * GET /api/seckill/order/list?userId={userId}
     */
    @GetMapping("/order/list")
    public Result listMyOrders(@RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = UserHolder.getUserId();
        }
        if (userId == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(orderService.listMyOrders(userId));
    }

    /**
     * 查询订单进度
     *
     * @param token 排队 token（UUID）
     * @return 订单状态描述
     */
    @GetMapping("/order/status/{token}")
    public Result queryOrderStatus(@PathVariable String token) {
        String status = orderService.queryOrderStatus(token);
        return Result.ok(status);
    }
}
