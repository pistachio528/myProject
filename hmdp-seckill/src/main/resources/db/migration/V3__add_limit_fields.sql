-- =============================================
-- V3: 为 seckill_voucher 表添加限购字段
-- =============================================

ALTER TABLE `seckill_voucher`
    ADD COLUMN `limit_type` VARCHAR(16) NOT NULL DEFAULT 'ONE_PER_USER'
        COMMENT '限购类型：ONE_PER_USER/ONE_PER_DAY/N_PER_USER' AFTER `type`,
    ADD COLUMN `max_count`  INT         NOT NULL DEFAULT 1
        COMMENT '最大购买次数（N_PER_USER 时有效）' AFTER `limit_type`;
