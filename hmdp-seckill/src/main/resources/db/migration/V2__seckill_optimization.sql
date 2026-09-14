-- =============================================
-- V2: 秒杀优化核心表结构
-- =============================================

-- 秒杀订单表
-- 异步消费者落库的最终订单记录，uk_user_voucher 作为幂等第二道防线
CREATE TABLE IF NOT EXISTS `voucher_order` (
    `id`          BIGINT   NOT NULL COMMENT '订单ID（雪花算法）',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `voucher_id`  BIGINT   NOT NULL COMMENT '优惠券ID',
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1=处理中 2=已创建 3=创建失败',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 幂等第二道防线：同一用户对同一优惠券只能有一条订单
    UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- 本地消息表
-- 与业务数据同库，保证库存扣减与 MQ 投递的事务一致性
CREATE TABLE IF NOT EXISTS `local_message` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `voucher_id`  BIGINT      NOT NULL COMMENT '优惠券ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `token`       VARCHAR(36) NOT NULL COMMENT '排队token（UUID）',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                              COMMENT '消息状态：PENDING/SENT/DEAD/REPLENISHED',
    `retry_count` INT         NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 重试扫描索引：按状态+创建时间快速定位超时 PENDING 消息
    INDEX `idx_status_create` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（可靠投递）';
