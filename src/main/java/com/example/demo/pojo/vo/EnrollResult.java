package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册一个数字模板的成功返回。
 */
@Schema(description = "注册一个数字模板的结果")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrollResult {

    @Schema(description = "模板 ID", example = "1024")
    private Long profileId;

    @Schema(description = "数字标签：DIGIT_0 ~ DIGIT_9", example = "DIGIT_3")
    private String phraseTag;

    @Schema(description = "MFCC 帧数", example = "68")
    private Integer frames;

    @Schema(description = "录音时长（毫秒）", example = "720")
    private Integer durationMs;

    @Schema(description = "0-100 注册质量分", example = "88")
    private Integer quality;
}
