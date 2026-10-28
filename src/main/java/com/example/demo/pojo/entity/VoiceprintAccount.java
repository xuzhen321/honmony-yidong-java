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
 * 声纹档案：一个 user 永远只有一行。
 *
 * <p>这张表没有 deleted 字段。重置声纹只把 status 改成 2，行永远保留，
 * 否则 {@code UNIQUE(user_id)} 会让重新注册插不进去。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("voiceprint_account")
public class VoiceprintAccount {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 逻辑外键 -> user.id */
    @TableField("user_id")
    private Long userId;

    /** 0 未完成 / 1 已注册 / 2 已失效 */
    @TableField("status")
    private Integer status;

    /** 10 个数字模板全部录完的时间 */
    @TableField("enrolled_at")
    private LocalDateTime enrolledAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
