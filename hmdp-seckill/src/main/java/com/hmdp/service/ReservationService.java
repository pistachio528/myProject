package com.hmdp.service;

import com.hmdp.dto.ReservationDTO;
import com.hmdp.entity.Reservation;

import java.util.List;

/**
 * 餐厅预约服务接口
 */
public interface ReservationService {

    /**
     * 创建预约
     */
    Reservation create(Long userId, ReservationDTO dto);

    /**
     * 取消预约
     * @return true=取消成功，false=预约不存在或无权操作
     */
    boolean cancel(Long reservationId, Long userId);

    /**
     * 查询用户的预约列表（不含已取消）
     */
    List<Reservation> listByUser(Long userId);

    /**
     * 查询预约详情
     */
    Reservation getById(Long reservationId, Long userId);
}
