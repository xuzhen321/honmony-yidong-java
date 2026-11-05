-- ============================================================
-- 声纹认证 + 考勤任务 · 数据库脚本（课设简化版）
-- 数据库：harmony_test
--
-- 执行方式（在项目根目录）：
--   mysql -uroot -p harmony_test < src/main/resources/db/voiceprint.sql
--
-- 说明：
--   - 全部使用逻辑外键，数据库层没有 FOREIGN KEY；
--   - 只使用 CREATE TABLE IF NOT EXISTS，不包含 DROP / 自动迁移；
--   - 课设只保留主流程字段，算法参数固定在代码里：
--     16kHz / 16bit / 单声道、25ms 帧长 / 10ms 帧移、
--     13 维 MFCC、26 个 Mel 滤波器、512 点 FFT；
--   - 表已存在但字段不一致时，另写迁移脚本，不要直接改已上线的库。
-- ============================================================

USE `harmony_test`;

-- ------------------------------------------------------------
-- 1. 声纹档案：一个 user 永远只有一行
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `voiceprint_account`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT      NOT NULL COMMENT '逻辑外键 -> user.id',
    `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0未完成 1已注册 2已失效',
    `enrolled_at` DATETIME(3) DEFAULT NULL COMMENT '10 个数字模板全部录完的时间',
    `revoked_at`  DATETIME(3) DEFAULT NULL COMMENT '最后一次失效时间',
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vpa_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='声纹档案（一人一行）';

-- ------------------------------------------------------------
-- 2. 数字模板：每人每个数字最多 2 条
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `voiceprint_profile`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT      NOT NULL COMMENT '逻辑外键 -> user.id',
    `phrase_tag`      VARCHAR(16) NOT NULL COMMENT 'DIGIT_0 ~ DIGIT_9',
    `template_cipher` LONGBLOB    NOT NULL COMMENT 'AES-256-GCM 密文：nonce(12) + ciphertext + tag(16)',
    `template_sha256` CHAR(64)    NOT NULL COMMENT '明文 MFCC 矩阵 SHA-256，用于去重',
    `frames`          INT         NOT NULL COMMENT 'MFCC 帧数',
    `duration_ms`     INT         DEFAULT NULL COMMENT '录音时长（毫秒）',
    `quality`         SMALLINT    DEFAULT NULL COMMENT '0-100 注册质量分',
    `deleted`         TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vp_sha` (`template_sha256`),
    KEY `idx_vp_user_tag` (`user_id`, `phrase_tag`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='声纹模板';

-- ------------------------------------------------------------
-- 3. 挑战码：绑定 user + task，防重放
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `voice_challenge`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL COMMENT '逻辑外键 -> user.id',
    `task_id`    BIGINT      NOT NULL COMMENT '逻辑外键 -> attendance_task.id',
    `nonce`      CHAR(32)    NOT NULL COMMENT '一次性随机值',
    `digits`     VARCHAR(8)  NOT NULL COMMENT '4 位挑战码，如 3815',
    `expire_at`  DATETIME(3) NOT NULL COMMENT '签发时间 + 1 分钟',
    `used_at`    DATETIME(3) DEFAULT NULL COMMENT '非空即已消费',
    `attempts`   TINYINT     NOT NULL DEFAULT 0 COMMENT '失败尝试次数，上限 3',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ch_nonce` (`nonce`),
    KEY `idx_ch_task_user` (`task_id`, `user_id`),
    KEY `idx_ch_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='声纹挑战码';

-- ------------------------------------------------------------
-- 4. 每次打卡尝试：成功/失败都记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `voice_attempt`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `challenge_id`    BIGINT       DEFAULT NULL COMMENT '逻辑外键 -> voice_challenge.id',
    `user_id`         BIGINT       NOT NULL COMMENT '逻辑外键 -> user.id',
    `task_id`         BIGINT       NOT NULL COMMENT '逻辑外键 -> attendance_task.id',
    `audio_sha256`    CHAR(64)     NOT NULL COMMENT '明文音频 SHA-256，防重放',
    `duration_ms`     INT          DEFAULT NULL,
    `seg_count`       TINYINT      NOT NULL DEFAULT 0 COMMENT 'VAD 切出的段数，正常应为 4',
    `detected_digits` VARCHAR(8)   DEFAULT NULL COMMENT '实际识别出的数字，如 3875',
    `seg_scores`      JSON         DEFAULT NULL COMMENT '每段最高分，如 [0.82,0.79,0.61,0.88]',
    `avg_score`       DECIMAL(6,4) DEFAULT NULL,
    `min_score`       DECIMAL(6,4) DEFAULT NULL,
    `result`          VARCHAR(8)   NOT NULL COMMENT 'PASS / FAIL',
    `fail_reason`     VARCHAR(128) DEFAULT NULL COMMENT '失败原因，直接存给用户看的 msg',
    `vp_profile_id`   BIGINT       DEFAULT NULL COMMENT '命中的模板 ID（最高分那条）',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_va_user_time` (`user_id`, `created_at`),
    KEY `idx_va_task_user` (`task_id`, `user_id`),
    KEY `idx_va_audio` (`audio_sha256`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='声纹打卡尝试明细';

-- ------------------------------------------------------------
-- 5. 考勤任务：管理员发起，指定 IN/OUT 和时间窗口
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `attendance_task`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `title`      VARCHAR(64) NOT NULL COMMENT '任务名称，如 2026-10-28 上班打卡',
    `check_type` VARCHAR(8)  NOT NULL COMMENT 'IN 上班 / OUT 下班，由管理员指定',
    `start_at`   DATETIME(3)   NOT NULL COMMENT '允许打卡开始时间',
    `end_at`     DATETIME(3)   NOT NULL COMMENT '允许打卡截止时间',
    `latitude`   DECIMAL(10, 7) DEFAULT NULL COMMENT '打卡点纬度 WGS84',
    `longitude`  DECIMAL(10, 7) DEFAULT NULL COMMENT '打卡点经度 WGS84',
    `radius_m`   INT            DEFAULT NULL COMMENT '允许打卡半径（米）',
    `address`    VARCHAR(128)   DEFAULT NULL COMMENT '地点名称，仅展示',
    `created_by` BIGINT         NOT NULL COMMENT '逻辑外键 -> user.id，创建人必须是 ADMIN',
    `created_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_at_window` (`start_at`, `end_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='考勤任务（管理员发起）';

-- ------------------------------------------------------------
-- 6. 考勤任务名单：管理员指定哪些人需要打卡
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `attendance_task_user`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `task_id`    BIGINT      NOT NULL COMMENT '逻辑外键 -> attendance_task.id',
    `user_id`    BIGINT      NOT NULL COMMENT '逻辑外键 -> user.id',
    `record_id`  BIGINT      DEFAULT NULL COMMENT '逻辑外键 -> attendance_record.id，非空即已打卡',
    `checked_at` DATETIME(3) DEFAULT NULL COMMENT '完成打卡时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_atu_task_user` (`task_id`, `user_id`),
    KEY `idx_atu_user_task` (`user_id`, `task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='考勤任务名单';

-- ------------------------------------------------------------
-- 7. 打卡结果：一个任务里一个用户只有一条
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `attendance_record`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL COMMENT '逻辑外键 -> user.id',
    `task_id`         BIGINT       NOT NULL COMMENT '逻辑外键 -> attendance_task.id',
    `record_date`     DATE         NOT NULL COMMENT '考勤日期',
    `check_type`      VARCHAR(8)   NOT NULL COMMENT '来自任务：IN 上班 / OUT 下班',
    `challenge_id`    BIGINT       DEFAULT NULL COMMENT '逻辑外键 -> voice_challenge.id',
    `detected_digits` VARCHAR(8)   DEFAULT NULL COMMENT '识别出的数字串',
    `vp_profile_id`   BIGINT       DEFAULT NULL COMMENT '命中的模板 ID',
    `match_score`     DECIMAL(6,4) DEFAULT NULL COMMENT '最高分',
    `audio_sha256`    CHAR(64)     DEFAULT NULL,
    `check_latitude`  DECIMAL(10, 7) DEFAULT NULL COMMENT '打卡时纬度 WGS84',
    `check_longitude` DECIMAL(10, 7) DEFAULT NULL COMMENT '打卡时经度 WGS84',
    `distance_m`      DECIMAL(8, 1)  DEFAULT NULL COMMENT '与打卡点距离（米）',
    `accuracy_m`      DECIMAL(8, 1)  DEFAULT NULL COMMENT '定位精度（米）',
    `is_mock`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否模拟位置',
    `source`          VARCHAR(8)   NOT NULL DEFAULT 'VOICE' COMMENT 'VOICE 声纹打卡 / MANUAL 管理员补卡',
    `operator_id`     BIGINT       DEFAULT NULL COMMENT '补卡/修改管理员 ID',
    `remark`          VARCHAR(128) DEFAULT NULL COMMENT '补卡备注',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ar_task_user` (`task_id`, `user_id`),
    KEY `idx_ar_user_date` (`user_id`, `record_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='考勤记录';

-- ------------------------------------------------------------
-- 8. 音频文件元数据：注册音频长期，打卡音频 7 天
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `media_object`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_type`   VARCHAR(16)  NOT NULL COMMENT 'VP_ENROLL / CHECKIN',
    `owner_id`     BIGINT       DEFAULT NULL COMMENT '注册->profile.id；打卡->attempt.id',
    `local_path`   VARCHAR(255) NOT NULL COMMENT '相对音频根目录的路径',
    `sha256`       CHAR(64)     NOT NULL COMMENT '明文音频 SHA-256',
    `retain_until` DATE         DEFAULT NULL COMMENT 'NULL=长期；打卡音频=打卡日+7天',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_path` (`local_path`),
    KEY `idx_media_sha` (`sha256`),
    KEY `idx_media_retain` (`retain_until`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='音频文件元数据';
