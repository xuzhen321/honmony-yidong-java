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
 * 考勤任务名单：管理员指定哪些人需要在某个任务里打卡。
 *
 * <p>{@code (task_id, user_id)} 唯一。是否已打卡只看 {@code recordId} / {@code checkedAt} 是否为空。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("attendance_task_user")
public class AttendanceTaskUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 逻辑外键 -> attendance_task.id */
    @TableField("task_id")
    private Long taskId;

    /** 逻辑外键 -> user.id */
    @TableField("user_id")
    private Long userId;

    /** 逻辑外键 -> attendance_record.id；非空即已打卡 */
    @TableField("record_id")
    private Long recordId;

    @TableField("checked_at")
    private LocalDateTime checkedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
