package com.example.demo.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 考勤任务：管理员发起，指定本次是上班还是下班，以及允许打卡的时间窗口。
 *
 * <p>任务创建后不可修改、不可删除；是否进行中由 startAt/endAt 推导。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("attendance_task")
public class AttendanceTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务名称，如 2026-10-28 上班打卡 */
    @TableField("title")
    private String title;

    /** IN 上班 / OUT 下班，由管理员指定 */
    @TableField("check_type")
    private String checkType;

    /** 允许打卡开始时间 */
    @TableField("start_at")
    private LocalDateTime startAt;

    /** 允许打卡截止时间 */
    @TableField("end_at")
    private LocalDateTime endAt;

    /** 逻辑外键 -> user.id，创建人必须是 ADMIN */
    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
