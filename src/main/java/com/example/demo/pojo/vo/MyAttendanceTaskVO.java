package com.example.demo.pojo.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "我的考勤任务")
@Data
public class MyAttendanceTaskVO {

    @Schema(description = "任务 ID", example = "501")
    private Long taskId;

    @Schema(description = "任务名称", example = "2026-10-28 上班打卡")
    private String title;

    @Schema(description = "IN 上班 / OUT 下班", example = "IN")
    private String checkType;

    @Schema(description = "允许打卡开始时间")
    private LocalDateTime startAt;

    @Schema(description = "允许打卡截止时间")
    private LocalDateTime endAt;

    @Schema(description = "UPCOMING 未开始 / OPEN 进行中 / CLOSED 已结束", example = "OPEN")
    private String state;

    @Schema(description = "本任务是否已完成打卡", example = "false")
    private Boolean done;

    @Schema(description = "完成打卡时间")
    private LocalDateTime checkedAt;

    /** 打卡记录 ID，只在 Service 里用来换算 done，不返回给前端 */
    @JsonIgnore
    private Long recordId;
}
