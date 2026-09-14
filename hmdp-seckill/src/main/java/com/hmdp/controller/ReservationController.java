package com.hmdp.controller;

import com.hmdp.dto.ReservationDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;
import com.hmdp.service.ReservationService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 餐厅预约控制器
 *
 * POST   /api/reservation          创建预约
 * DELETE /api/reservation/{id}     取消预约
 * GET    /api/reservation/list     查询我的预约列表
 * GET    /api/reservation/{id}     查询预约详情
 */
@RestController
@RequestMapping("/api/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 创建预约
     * 支持两种调用方式：
     * 1. 用户直接调用（从 ThreadLocal 取 userId）
     * 2. 智能客服代调（通过 dto.userId 传入，需已通过鉴权）
     */
    @PostMapping
    public Result create(@RequestBody ReservationDTO dto) {
        Long userId = dto.getUserId() != null ? dto.getUserId() : UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        // 参数校验
        if (dto.getShopName() == null || dto.getShopName().isBlank()) {
            return Result.fail("请提供商家名称");
        }
        if (dto.getReserveDate() == null || dto.getReserveDate().isBlank()) {
            return Result.fail("请提供预约日期（格式：yyyy-MM-dd）");
        }
        if (dto.getReserveTime() == null || dto.getReserveTime().isBlank()) {
            return Result.fail("请提供预约时间（格式：HH:mm，如 18:30）");
        }
        if (dto.getPeopleCount() == null || dto.getPeopleCount() < 1 || dto.getPeopleCount() > 20) {
            return Result.fail("就餐人数需在 1-20 之间");
        }
        try {
            Reservation reservation = reservationService.create(userId, dto);
            return Result.ok(reservation);
        } catch (Exception e) {
            return Result.fail("预约失败：" + e.getMessage());
        }
    }

    /**
     * 取消预约
     */
    @DeleteMapping("/{id}")
    public Result cancel(@PathVariable Long id,
                         @RequestParam(required = false) Long userId) {
        Long currentUserId = userId != null ? userId : UserHolder.getUserId();
        if (currentUserId == null) {
            return Result.fail("请先登录");
        }
        boolean success = reservationService.cancel(id, currentUserId);
        return success ? Result.ok("预约已取消") : Result.fail("取消失败，预约不存在或无权操作");
    }

    /**
     * 查询我的预约列表
     */
    @GetMapping("/list")
    public Result list(@RequestParam(required = false) Long userId) {
        Long currentUserId = userId != null ? userId : UserHolder.getUserId();
        if (currentUserId == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(reservationService.listByUser(currentUserId));
    }

    /**
     * 查询预约详情
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id,
                         @RequestParam(required = false) Long userId) {
        Long currentUserId = userId != null ? userId : UserHolder.getUserId();
        if (currentUserId == null) {
            return Result.fail("请先登录");
        }
        Reservation reservation = reservationService.getById(id, currentUserId);
        return reservation != null ? Result.ok(reservation) : Result.fail("预约不存在");
    }
}
