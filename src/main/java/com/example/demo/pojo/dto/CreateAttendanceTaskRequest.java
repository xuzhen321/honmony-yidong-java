package com.example.demo.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理员创建考勤任务的入参。
 */
@Data
@Schema(description = "创建考勤任务请求")
public class CreateAttendanceTaskRequest {

    @Schema(description = "任务名称；不传默认按日期和类型生成", example = "2026-10-28 上班打卡")
    @Size(max = 64, message = "任务名称不能超过 64 个字符")
    private String title;

    @Schema(description = "IN 上班 / OUT 下班", example = "IN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "checkType 不能为空")
    @Pattern(regexp = "IN|OUT", message = "checkType 只能是 IN 或 OUT")
    private String checkType;

    @Schema(description = "允许打卡开始时间，ISO-8601 带时区",
            example = "2026-10-28T08:00:00+08:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "startAt 不能为空")
    private OffsetDateTime startAt;

    @Schema(description = "允许打卡截止时间，必须晚于 startAt",
            example = "2026-10-28T09:30:00+08:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "endAt 不能为空")
    private OffsetDateTime endAt;

    @Schema(description = "指定人员 ID 列表，去重后 1~500 个", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "userIds 不能为空")
    @Size(max = 500, message = "一次最多指定 500 人")
    private List<Long> userIds;
}
