package com.example.demo.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤结果：一个任务里一个用户只有一条成功记录。
 *
 * <p>{@code checkType} 是打卡时的快照，直接取任务的值。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("attendance_record")
public class AttendanceRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 逻辑外键 -> user.id */
    @TableField("user_id")
    private Long userId;

    /** 逻辑外键 -> attendance_task.id */
    @TableField("task_id")
    private Long taskId;

    /** 考勤日期 */
    @TableField("record_date")
    private LocalDate recordDate;

    /** 来自任务：IN 上班 / OUT 下班 */
    @TableField("check_type")
    private String checkType;

    /** 逻辑外键 -> voice_challenge.id */
    @TableField("challenge_id")
    private Long challengeId;

    @TableField("detected_digits")
    private String detectedDigits;

    @TableField("vp_profile_id")
    private Long vpProfileId;

    @TableField("match_score")
    private BigDecimal matchScore;

    @TableField("audio_sha256")
    private String audioSha256;

    /** 逻辑删除：0 正常，1 已删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
