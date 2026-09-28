-- =====================================================
-- V5 增量：售后（仅退款）闭环
-- 星选百货售后政策：已支付订单支付后 7 天内可申请【仅退款】（无退货物流），
-- 管理员人工审批；拒绝后用户可重新申请新单。
-- 防重复申请：不加 (order_row_id, status=0) 式唯一键（MySQL 无 partial index，
-- 终态重申请会撞键），由 AftersaleServiceImpl#apply 事务内 FOR UPDATE 锁父订单行保证。
-- =====================================================
-- 连接字符集显式 utf8mb4（防客户端默认 latin1 导致中文乱码）
SET NAMES utf8mb4;

USE shopmanagement;

CREATE TABLE IF NOT EXISTS `aftersale_record` (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `aftersale_id`  varchar(32)  NOT NULL COMMENT '售后单号（IdWorker 生成）',
    `order_id`      varchar(20)  NOT NULL COMMENT '订单号（order.order_id）',
    `order_row_id`  int          NOT NULL COMMENT '订单行ID（order.id）',
    `user_id`       int          NOT NULL,
    `product_id`    int          NOT NULL,
    `product_num`   int          NOT NULL DEFAULT 1 COMMENT '售后数量（申请时订单行快照）',
    `refund_amount` double       NOT NULL COMMENT '退款金额 = 行价格*数量（申请时快照）',
    `reason`        varchar(200) NOT NULL COMMENT '用户申请理由',
    `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '0待处理 1同意(退款完成) 2拒绝',
    `reject_reason` varchar(200) NULL COMMENT '拒绝理由（拒绝时必填）',
    `apply_time`    bigint       NOT NULL COMMENT '申请时间戳(ms)',
    `handle_time`   bigint       NULL COMMENT '处理时间戳(ms)',
    `handler_id`    int          NULL COMMENT '处理管理员 userId',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_aftersale_id` (`aftersale_id`),
    KEY `idx_aftersale_user` (`user_id`),
    KEY `idx_aftersale_row` (`order_row_id`),
    KEY `idx_aftersale_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '售后申请（仅退款）；归属 order 服务读写';