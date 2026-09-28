-- =====================================================
-- V3 增量：管理端统计所需字段（user 注册时间）
-- 执行：mysql -uroot -p shopmanagement < sql/V3__admin_stats.sql
-- =====================================================
USE shopmanagement;

-- 用户注册时间（存量行填充为迁移时刻——用户增长曲线从上线开始准确累积）
ALTER TABLE `user`
    ADD COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间';