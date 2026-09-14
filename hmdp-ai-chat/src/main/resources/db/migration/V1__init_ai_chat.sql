-- =============================================
-- V1: 智能客服模块初始化表结构
-- =============================================

-- 1. 会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`            VARCHAR(36)  NOT NULL COMMENT '会话ID（UUID）',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=活跃 2=已结束',
    `message_count` INT          NOT NULL DEFAULT 0 COMMENT '消息总数',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `end_time`      DATETIME     NULL COMMENT '会话结束时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_status` (`user_id`, `status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能客服会话表';

-- 2. 消息归档表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`   VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `role`         VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system/tool',
    `content`      TEXT         NOT NULL COMMENT '消息内容',
    `message_type` VARCHAR(16)  NOT NULL DEFAULT 'text' COMMENT 'text/card/function_call',
    `metadata`     JSON         NULL COMMENT '元数据（token用量、工具调用详情等）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_session_time` (`session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能客服消息归档表';

-- 3. 用户反馈表
CREATE TABLE IF NOT EXISTS `chat_feedback` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`    VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `message_id`    BIGINT       NOT NULL COMMENT '被反馈的消息ID',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `feedback_type` VARCHAR(32)  NOT NULL COMMENT 'wrong_entity/irrelevant/inaccurate/other',
    `detail`        VARCHAR(512) NULL COMMENT '用户补充说明',
    `resolved`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未处理 1=已重新回复',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_session` (`session_id`),
    INDEX `idx_type_time` (`feedback_type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户纠错反馈表';

-- 4. 知识库文档元数据表
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `doc_type`    VARCHAR(16)  NOT NULL COMMENT 'faq/rule/product',
    `title`       VARCHAR(256) NOT NULL COMMENT '文档标题',
    `content`     TEXT         NOT NULL COMMENT '原始文档内容',
    `chunk_count` INT          NOT NULL DEFAULT 0 COMMENT '分块数量',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=已索引 2=索引中 3=已删除',
    `version`     INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_type_status` (`doc_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档元数据表';

-- 5. 意图识别日志表
CREATE TABLE IF NOT EXISTS `intent_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`     VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `user_message`   TEXT         NOT NULL COMMENT '用户原始消息',
    `intent`         VARCHAR(64)  NULL COMMENT '识别到的意图',
    `confidence`     DECIMAL(3,2) NOT NULL COMMENT '置信度分数 0.00-1.00',
    `disambiguation` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否触发消歧 0=否 1=自动消歧 2=用户确认',
    `user_confirmed` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否触发用户确认',
    `context_signals` JSON        NULL COMMENT '使用的上下文信号',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_session` (`session_id`),
    INDEX `idx_confidence` (`confidence`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图识别日志表';
