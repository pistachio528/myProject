-- =============================================
-- V1: 初始化基础表结构与测试数据
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号',
    `nick_name`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 秒杀优惠券表
CREATE TABLE IF NOT EXISTS `seckill_voucher` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
    `title`        VARCHAR(128) NOT NULL COMMENT '标题',
    `sub_title`    VARCHAR(256) DEFAULT NULL COMMENT '副标题',
    `stock`        INT          NOT NULL DEFAULT 0 COMMENT '库存',
    `pay_value`    BIGINT       NOT NULL DEFAULT 0 COMMENT '支付金额（分）',
    `actual_value` BIGINT       NOT NULL DEFAULT 0 COMMENT '实际价值（分）',
    `begin_time`   DATETIME     NOT NULL COMMENT '活动开始时间',
    `end_time`     DATETIME     NOT NULL COMMENT '活动结束时间',
    `type`         TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1=秒杀券',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀优惠券表';

-- 测试数据：插入 2 条秒杀券
INSERT INTO `seckill_voucher` (`title`, `sub_title`, `stock`, `pay_value`, `actual_value`, `begin_time`, `end_time`, `type`)
VALUES
    ('周年庆特惠券', '限时秒杀，先到先得', 100, 9900, 50000,
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 23 HOUR), 1),
    ('新人专属优惠券', '新用户专享，每人限购1张', 50, 4900, 20000,
     DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_ADD(NOW(), INTERVAL 47 HOUR), 1);
