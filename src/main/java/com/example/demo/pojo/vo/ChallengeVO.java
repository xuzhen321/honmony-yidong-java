package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 声纹打卡挑战码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "声纹打卡挑战码")
public class ChallengeVO {

    @Schema(description = "考勤任务 ID", example = "501")
    private Long taskId;

    @Schema(description = "一次性随机值，提交打卡时原样带回")
    private String nonce;

    @Schema(description = "4 位挑战码，如 3815", example = "3815")
    private String digits;

    @Schema(description = "过期时间（服务器本地时间）")
    private LocalDateTime expireAt;

    @Schema(description = "有效期（秒）", example = "60")
    private int ttlSeconds;

    @Schema(description = "给用户看的朗读提示")
    private String prompt;
}
