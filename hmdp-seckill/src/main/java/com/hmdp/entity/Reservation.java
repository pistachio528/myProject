package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 餐厅预约实体
 */
@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 商家名称 */
    private String shopName;

    /** 预约日期 */
    private LocalDate reserveDate;

    /** 预约时间（如 18:30） */
    private String reserveTime;

    /** 就餐人数 */
    private Integer peopleCount;

    /** 备注 */
    private String remark;

    /**
     * 状态：1=待确认 2=已确认 3=已取消 4=已完成
     */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
