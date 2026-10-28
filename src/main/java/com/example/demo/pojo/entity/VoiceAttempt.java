package com.example.demo.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 每次声纹打卡尝试：成功/失败都要记录，作为实验数据和申诉证据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("voice_attempt")
public class VoiceAttempt {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 逻辑外键 -> voice_challenge.id */
    @TableField("challenge_id")
    private Long challengeId;

    /** 逻辑外键 -> user.id */
    @TableField("user_id")
    private Long userId;

    /** 逻辑外键 -> attendance_task.id */
    @TableField("task_id")
    private Long taskId;

    /** 明文音频 SHA-256，防重放 */
    @TableField("audio_sha256")
    private String audioSha256;

    @TableField("duration_ms")
    private Integer durationMs;

    /** VAD 切出的段数，正常应为 4 */
    @TableField("seg_count")
    private Integer segCount;

    /** 实际识别出的数字，如 3875 */
    @TableField("detected_digits")
    private String detectedDigits;

    /** 每段最高分，JSON 数组，如 [0.82,0.79,0.61,0.88] */
    @TableField("seg_scores")
    private String segScores;

    @TableField("avg_score")
    private BigDecimal avgScore;

    @TableField("min_score")
    private BigDecimal minScore;

    /** PASS / FAIL */
    @TableField("result")
    private String result;

    /** 失败原因，直接存给用户看的 msg */
    @TableField("fail_reason")
    private String failReason;

    /** 命中的模板 ID（最高分那条） */
    @TableField("vp_profile_id")
    private Long vpProfileId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
