package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "考勤任务详情")
@Data
public class AttendanceTaskDetailVO {

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

    @Schema(description = "任务名单")
    private List<AttendanceTaskParticipantVO> participants;
}
