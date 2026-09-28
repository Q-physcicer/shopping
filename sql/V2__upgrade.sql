-- =====================================================
-- V2 增量升级：分布式架构 + 订单状态机 + 支付 + 秒杀消息表
-- 基线见 V1__baseline.sql（原 shopmanagement.sql）
-- 执行前提：已按 V1 建库（9 张表 + 初始数据）
-- =====================================================

USE shopmanagement;

-- 1) 用户表：增加角色（USER/ADMIN）；密码列扩容以容纳 BCrypt(60-72字符)
ALTER TABLE `user`
    ADD COLUMN `role` varchar(10) NOT NULL DEFAULT 'USER' COMMENT '角色 USER/ADMIN',
    MODIFY COLUMN `password` varchar(72) NOT NULL COMMENT '密码(BCrypt,兼容历史MD5)';

-- 指定管理员；若库中无 admin 用户，插入初始密码 admin
-- （密码以历史 MD5 格式落库，P1 的 BCrypt 透明升级逻辑会在首次登录时自动升级）
UPDATE `user` SET `role` = 'ADMIN' WHERE `username` = 'admin';
INSERT INTO `user` (`username`, `password`, `role`)
SELECT 'admin', MD5('admin'), 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'admin');

-- 2) 订单表：状态机（工单流转）/支付时间/秒杀关联 + 索引
ALTER TABLE `order`
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消(超时/用户) 3已完成 4删除',
    ADD COLUMN `pay_time` bigint NULL COMMENT '支付时间戳(ms)',
    ADD COLUMN `seckill_id` int NULL COMMENT '秒杀活动ID(秒杀单才有)',
    ADD INDEX idx_order_user (user_id),
    ADD INDEX idx_order_id (order_id),
    ADD INDEX idx_order_status (status);

-- 存量订单视为已支付
UPDATE `order` SET `status` = 1 WHERE `status` = 0;

-- 3) 秒杀本地消息表（原工程 XML 已引用但从未建表）
CREATE TABLE IF NOT EXISTS `seckill_message_record` (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `message_id`    varchar(64)  NOT NULL COMMENT '业务消息ID = seckillId:userId',
    `user_id`       varchar(20)  NOT NULL,
    `seckill_id`    varchar(20)  NOT NULL,
    `product_id`    varchar(20)  NULL,
    `status`        varchar(16)  NOT NULL DEFAULT 'SENT' COMMENT 'SENT/CONSUMED/FAILED/CANCELLED',
    `retry_count`   int          NOT NULL DEFAULT 0,
    `error_message` varchar(255) NULL,
    `created_at`    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY uk_seckill_message (message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀订单消息记录';

-- 4) 模拟支付流水表（P6 支付闭环使用）
CREATE TABLE IF NOT EXISTS `payment_record` (
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `pay_no`      varchar(32)  NOT NULL COMMENT '支付流水号',
    `order_id`    varchar(20)  NOT NULL,
    `user_id`     int          NOT NULL,
    `amount`      double       NOT NULL,
    `pay_channel` varchar(16)  NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK',
    `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '0发起 1成功 2失败',
    `created_at`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY uk_pay_no (pay_no),
    KEY idx_pay_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='支付流水';

-- 5) 数据修复：轮播图绝对域名相对化（配合 Nginx 静态托管 public/imgs）
UPDATE `carousel` SET `img_path` = 'public/imgs/cms_1.jpg' WHERE carousel_id = 1;
UPDATE `carousel` SET `img_path` = 'public/imgs/cms_2.jpg' WHERE carousel_id = 2;
UPDATE `carousel` SET `img_path` = 'public/imgs/cms_3.jpg' WHERE carousel_id = 3;
UPDATE `carousel` SET `img_path` = 'public/imgs/cms_4.jpg' WHERE carousel_id = 4;

-- 验收：DESC user; DESC `order`; DESC seckill_message_record; DESC payment_record;