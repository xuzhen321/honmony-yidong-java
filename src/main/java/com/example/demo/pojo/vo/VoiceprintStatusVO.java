package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前登录用户的声纹注册状态。
 */
@Schema(description = "声纹注册状态")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoiceprintStatusVO {

    @Schema(description = "NOT_ENROLLED 未注册 / ENROLLING 录入中 / ACTIVE 已生效 / REVOKED 已失效",
            example = "ENROLLING")
    private String status;

    @Schema(description = "是否录了整句模板；本期恒为 false", example = "false")
    private Boolean hasFixedPhrase;

    @Schema(description = "已完成的数字种类数（0~10）", example = "9")
    private Integer digitCount;

    @Schema(description = "还缺哪些数字，如 [\"7\"]")
    private List<String> missingDigits;

    @Schema(description = "10 个数字齐全、可以打卡时为 true", example = "false")
    private Boolean readyForCheckin;

    @Schema(description = "10 个数字全部录完的时间；未完成时为 null")
    private LocalDateTime enrolledAt;
}
