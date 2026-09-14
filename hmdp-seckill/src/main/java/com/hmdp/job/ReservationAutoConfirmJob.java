package com.hmdp.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.entity.Reservation;
import com.hmdp.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约自动确认定时任务
 * 每分钟扫描一次状态为"待确认"（status=1）的预约
 * 若创建时间超过1分钟，自动将状态更新为"已确认"（status=2）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationAutoConfirmJob {

    private final ReservationMapper reservationMapper;

    @Scheduled(fixedDelay = 60_000) // 每60秒执行一次
    public void autoConfirm() {
        // 查询所有待确认且创建时间超过1分钟的预约
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(1);

        List<Reservation> pendingList = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getStatus, 1)          // 待确认
                        .le(Reservation::getCreateTime, threshold) // 创建时间 <= 1分钟前
        );

        if (pendingList.isEmpty()) {
            return;
        }

        int count = reservationMapper.update(null,
                new LambdaUpdateWrapper<Reservation>()
                        .eq(Reservation::getStatus, 1)
                        .le(Reservation::getCreateTime, threshold)
                        .set(Reservation::getStatus, 2)           // 已确认
                        .set(Reservation::getUpdateTime, LocalDateTime.now())
        );

        log.info("ReservationAutoConfirmJob: auto-confirmed {} reservations", count);
    }
}
