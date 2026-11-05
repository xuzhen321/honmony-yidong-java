package com.example.demo.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 管理员手动补卡入参。
 */
@Data
@Schema(description = "管理员手动补卡请求")
public class ManualCheckinRequest {

    @Schema(description = "考勤任务 ID", example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "taskId 不能为空")
    private Long taskId;

    @Schema(description = "员工用户 ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "userId 不能为空")
    private Long userId;

    @Schema(description = "实际打卡时间（带时区）；不传默认当前时间",
            example = "2026-10-29T08:30:00+08:00")
    private OffsetDateTime checkAt;

    @Schema(description = "补卡备注，最长 128", example = "忘记打卡，管理员补录")
    @Size(max = 128, message = "补卡备注不能超过 128 个字符")
    private String remark;
}
