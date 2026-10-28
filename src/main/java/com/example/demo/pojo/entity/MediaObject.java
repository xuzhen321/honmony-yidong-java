package com.example.demo.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 音频文件元数据。
 *
 * <p>课设简化版：音频固定本地 AES-GCM 存储，只记录路径、哈希和保留时间。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("media_object")
public class MediaObject {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** VP_ENROLL / CHECKIN */
    @TableField("owner_type")
    private String ownerType;

    /** 注册 -> profile.id；打卡 -> attempt.id */
    @TableField("owner_id")
    private Long ownerId;

    /** 相对音频根目录的路径 */
    @TableField("local_path")
    private String localPath;

    /** 明文音频 SHA-256 */
    @TableField("sha256")
    private String sha256;

    /** NULL = 长期；打卡音频 = 打卡日 + 7 天 */
    @TableField("retain_until")
    private LocalDate retainUntil;

    /** 逻辑删除：0 正常，1 已删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
