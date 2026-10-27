-- ============================================================
-- 用户登录模块 · 数据库初始化脚本
-- 数据库：harmony_test
-- 用户表：user
--
-- 执行方式（在项目根目录）：
--   mysql -uroot -p < src/main/resources/db/init.sql
--
-- 脚本可重复执行：
--   - 建库 / 建表使用 IF NOT EXISTS
--   - 测试账号使用 INSERT IGNORE，不会重复插入
-- ============================================================

CREATE DATABASE IF NOT EXISTS `harmony_test`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `harmony_test`;

CREATE TABLE IF NOT EXISTS `user`
(
    `id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_name` VARCHAR(32)  NOT NULL COMMENT '登录账号',
    `password`  VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    `nick_name` VARCHAR(32)  DEFAULT '新用户' COMMENT '昵称',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_user_name` (`user_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户表';

-- ------------------------------------------------------------
-- 内置测试账号（密码都是 123456，存的是 BCrypt 密文）
-- ------------------------------------------------------------
INSERT IGNORE INTO `user` (`id`, `user_name`, `password`, `nick_name`)
VALUES (1, 'admin', '$2a$10$DfYb9tFbOp8IZHaL/Lb1U.ydxHe7dIAr5/QaCH1EfFQOMvWs7G1.W', '管理员');
