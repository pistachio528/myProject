-- =============================================
-- V4: 新增餐厅预约功能表
-- =============================================

CREATE TABLE IF NOT EXISTS `reservation` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '预约ID',
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
    `shop_name`    VARCHAR(128) NOT NULL COMMENT '商家名称',
    `reserve_date` DATE         NOT NULL COMMENT '预约日期',
    `reserve_time` VARCHAR(10)  NOT NULL COMMENT '预约时间（如 18:30）',
    `people_count` INT          NOT NULL DEFAULT 2 COMMENT '就餐人数',
    `remark`       VARCHAR(256) DEFAULT NULL COMMENT '备注（特殊要求等）',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=待确认 2=已确认 3=已取消 4=已完成',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_shop_date` (`shop_name`, `reserve_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='餐厅预约表';
