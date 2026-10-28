package com.example.demo.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 数字模板：每人每个 phraseTag 最多 2 条。
 *
 * <p>课设简化版：算法参数固定（16k/16bit/单声道、13 维 MFCC），
 * 只存帧数用来还原矩阵；模板是否生效只看 deleted。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("voiceprint_profile")
public class VoiceprintProfile {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 逻辑外键 -> user.id */
    @TableField("user_id")
    private Long userId;

    /** DIGIT_0 ~ DIGIT_9 */
    @TableField("phrase_tag")
    private String phraseTag;

    /** AES-256-GCM 密文：nonce(12) + ciphertext + tag(16) */
    @TableField("template_cipher")
    @ToString.Exclude
    private byte[] templateCipher;

    /** 明文 MFCC 矩阵 SHA-256，用于去重 */
    @TableField("template_sha256")
    private String templateSha256;

    /** MFCC 帧数 */
    @TableField("frames")
    private Integer frames;

    /** 录音时长（毫秒） */
    @TableField("duration_ms")
    private Integer durationMs;

    /** 0-100 注册质量分 */
    @TableField("quality")
    private Integer quality;

    /** 逻辑删除：0 正常，1 已删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
