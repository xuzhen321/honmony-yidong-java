-- ============================================================
-- 声纹 + 位置打卡 / 管理员补卡 · 数据库迁移脚本
-- 数据库：harmony_test
--
-- 执行方式（在项目根目录，只执行一次）：
--   mysql -uroot -p harmony_test < src/main/resources/db/migration_checkin_location_manual.sql
--
-- 说明：
--   - 本脚本只做 ALTER TABLE，不包含 DROP / DELETE；
--   - 新装环境可以直接执行更新后的 voiceprint.sql，不要再执行本脚本；
--   - 已经执行过本脚本的环境不要重复执行，MySQL 会对重复列报错。
-- ============================================================

USE `harmony_test`;

-- ------------------------------------------------------------
-- 1. 考勤任务：可选打卡点（不填 = 不限位置）
-- ------------------------------------------------------------
ALTER TABLE `attendance_task`
    ADD COLUMN `latitude`  DECIMAL(10, 7) DEFAULT NULL COMMENT '打卡点纬度 WGS84',
    ADD COLUMN `longitude` DECIMAL(10, 7) DEFAULT NULL COMMENT '打卡点经度 WGS84',
    ADD COLUMN `radius_m`  INT            DEFAULT NULL COMMENT '允许打卡半径（米）',
    ADD COLUMN `address`   VARCHAR(128)   DEFAULT NULL COMMENT '地点名称，仅展示';

-- ------------------------------------------------------------
-- 2. 打卡结果：位置审计字段 + 管理员补卡审计字段
-- ------------------------------------------------------------
ALTER TABLE `attendance_record`
    ADD COLUMN `check_latitude`  DECIMAL(10, 7) DEFAULT NULL COMMENT '打卡时纬度 WGS84',
    ADD COLUMN `check_longitude` DECIMAL(10, 7) DEFAULT NULL COMMENT '打卡时经度 WGS84',
    ADD COLUMN `distance_m`      DECIMAL(8, 1)  DEFAULT NULL COMMENT '与打卡点距离（米）',
    ADD COLUMN `accuracy_m`      DECIMAL(8, 1)  DEFAULT NULL COMMENT '定位精度（米）',
    ADD COLUMN `is_mock`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否模拟位置',
    ADD COLUMN `source`          VARCHAR(8)   NOT NULL DEFAULT 'VOICE' COMMENT 'VOICE 声纹打卡 / MANUAL 管理员补卡',
    ADD COLUMN `operator_id`     BIGINT       DEFAULT NULL COMMENT '补卡/修改管理员 ID',
    ADD COLUMN `remark`          VARCHAR(128) DEFAULT NULL COMMENT '补卡备注';

-- 存量记录全部视为声纹打卡；新补卡记录由应用层写 MANUAL
UPDATE `attendance_record` SET `source` = 'VOICE' WHERE `source` IS NULL OR `source` = '';
