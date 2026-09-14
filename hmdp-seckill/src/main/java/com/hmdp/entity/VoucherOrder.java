package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀订单实体
 * <p>
 * 由异步消费者落库，uk_user_voucher 唯一索引作为幂等第二道防线。
 * </p>
 */
@Data
@TableName("voucher_order")
public class VoucherOrder {

    /** 订单ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 优惠券ID */
    private Long voucherId;

    /** 订单状态：1=处理中 2=已创建 3=创建失败 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
