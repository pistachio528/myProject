package com.hmdp.dto;

import lombok.Data;

/**
 * 创建预约请求 DTO
 */
@Data
public class ReservationDTO {

    /** 商家名称（必填） */
    private String shopName;

    /** 预约日期，格式 yyyy-MM-dd（必填） */
    private String reserveDate;

    /** 预约时间，格式 HH:mm，如 18:30（必填） */
    private String reserveTime;

    /** 就餐人数（必填，1-20） */
    private Integer peopleCount;

    /** 备注（可选，如：靠窗位置、需要儿童椅等） */
    private String remark;

    /** 用户ID（由智能客服系统传入，普通用户无需填写） */
    private Long userId;
}
