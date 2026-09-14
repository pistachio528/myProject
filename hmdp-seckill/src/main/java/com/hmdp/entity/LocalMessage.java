package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表实体
 * <p>
 * 与业务数据同库，保证库存扣减与 MQ 投递的事务一致性。
 * 状态流转：PENDING → SENT（投递成功）→ DEAD（重试超限）→ REPLENISHED（库存已回补）
 * </p>
 */
@Data
@TableName("local_message")
public class LocalMessage {

    /** 消息ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 优惠券ID */
    private Long voucherId;

    /** 用户ID */
    private Long userId;

    /** 排队 token（UUID） */
    private String token;

    /** 消息状态：PENDING/SENT/DEAD/REPLENISHED */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
