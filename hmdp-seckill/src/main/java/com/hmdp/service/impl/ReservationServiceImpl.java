package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.ReservationDTO;
import com.hmdp.entity.Reservation;
import com.hmdp.mapper.ReservationMapper;
import com.hmdp.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;

    @Override
    public Reservation create(Long userId, ReservationDTO dto) {
        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setShopName(dto.getShopName());
        reservation.setReserveDate(LocalDate.parse(dto.getReserveDate(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        reservation.setReserveTime(dto.getReserveTime());
        reservation.setPeopleCount(dto.getPeopleCount());
        reservation.setRemark(dto.getRemark());
        reservation.setStatus(1); // 待确认
        reservation.setCreateTime(LocalDateTime.now());
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.insert(reservation);
        log.info("Reservation created: id={}, userId={}, shop={}, date={} {}",
                reservation.getId(), userId, dto.getShopName(),
                dto.getReserveDate(), dto.getReserveTime());
        return reservation;
    }

    @Override
    public boolean cancel(Long reservationId, Long userId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            log.warn("Reservation not found: id={}", reservationId);
            return false;
        }
        // 安全校验：只能取消自己的预约
        if (!reservation.getUserId().equals(userId)) {
            log.warn("Unauthorized cancel attempt: reservationId={}, userId={}, ownerId={}",
                    reservationId, userId, reservation.getUserId());
            return false;
        }
        // 已取消或已完成的不能再取消
        if (reservation.getStatus() == 3 || reservation.getStatus() == 4) {
            log.warn("Cannot cancel reservation in status {}: id={}", reservation.getStatus(), reservationId);
            return false;
        }
        reservation.setStatus(3); // 已取消
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        log.info("Reservation cancelled: id={}, userId={}", reservationId, userId);
        return true;
    }

    @Override
    public List<Reservation> listByUser(Long userId) {
        return reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getUserId, userId)
                        .ne(Reservation::getStatus, 3) // 排除已取消
                        .orderByDesc(Reservation::getReserveDate)
                        .orderByDesc(Reservation::getReserveTime)
        );
    }

    @Override
    public Reservation getById(Long reservationId, Long userId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || !reservation.getUserId().equals(userId)) {
            return null;
        }
        return reservation;
    }
}
