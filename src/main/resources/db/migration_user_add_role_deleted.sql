-- ============================================================
-- 已有 harmony_test.user 表：补充 role / deleted 字段
--
-- 只执行一次。如果重复执行，MySQL 会提示 Duplicate column name，
-- 说明字段已经加过了，可以忽略。
--
-- 执行方式（在项目根目录）：
--   mysql -uroot -p harmony_test < src/main/resources/db/migration_user_add_role_deleted.sql
-- ============================================================

USE `harmony_test`;

ALTER TABLE `user`
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER'
        COMMENT '角色：ADMIN 管理员 / USER 普通用户' AFTER `nick_name`,
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0
        COMMENT '逻辑删除：0 正常 1 已删除' AFTER `role`;

-- 现有内置管理员账号补成 ADMIN
UPDATE `user` SET `role` = 'ADMIN' WHERE `user_name` = 'admin';
