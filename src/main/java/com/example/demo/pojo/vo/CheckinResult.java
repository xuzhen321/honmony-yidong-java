package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 声纹 + 位置打卡成功结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "声纹 + 位置打卡结果")
public class CheckinResult {

    @Schema(description = "打卡记录 ID", example = "88")
    private Long recordId;

    @Schema(description = "考勤任务 ID", example = "501")
    private Long taskId;

    @Schema(description = "IN 上班 / OUT 下班", example = "IN")
    private String checkType;

    @Schema(description = "服务器确认的打卡时间")
    private LocalDateTime checkedAt;

    @Schema(description = "与打卡点的直线距离（米）；不限位置的任务为 null")
    private Double distanceM;

    @Schema(description = "允许打卡半径（米）；不限位置的任务为 null")
    private Integer radiusM;

    @Schema(description = "是否通过位置校验；不限位置的任务为 false")
    private Boolean locationVerified;

    @Schema(description = "客户端上报的定位精度（米）")
    private Double accuracy;

    @Schema(description = "客户端上报的模拟位置标记")
    private Boolean isMock;

    @Schema(description = "4 段平均相似度", example = "0.8421")
    private Double avgScore;

    @Schema(description = "4 段最低相似度", example = "0.7912")
    private Double minScore;

    @Schema(description = "后端识别出的数字串", example = "3815")
    private String detectedDigits;
}
