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
 * 声纹挑战码：一次性 nonce，绑定 user + task，用于防重放。
 *
 * <p>是否可用只看 {@code usedAt == null && expireAt > now}。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("voice_challenge")
public class VoiceChallenge {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 逻辑外键 -> user.id */
    @TableField("user_id")
    private Long userId;

    /** 逻辑外键 -> attendance_task.id */
    @TableField("task_id")
    private Long taskId;

    /** 一次性随机值 */
    @TableField("nonce")
    private String nonce;

    /** 4 位挑战码，如 3815 */
    @TableField("digits")
    private String digits;

    /** 签发时间 + 1 分钟 */
    @TableField("expire_at")
    private LocalDateTime expireAt;

    /** 非空即已消费 */
    @TableField("used_at")
    private LocalDateTime usedAt;

    /** 失败尝试次数，上限 3 */
    @TableField("attempts")
    private Integer attempts;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
