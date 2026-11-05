package com.example.demo.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 管理员修改补卡记录入参；字段为 null 表示不修改。
 */
@Data
@Schema(description = "管理员修改补卡记录请求")
public class UpdateManualRecordRequest {

    @Schema(description = "新的实际打卡时间（带时区）；不传表示不修改",
            example = "2026-10-29T08:35:00+08:00")
    private OffsetDateTime checkAt;

    @Schema(description = "新的备注；不传表示不修改，传空字符串表示清空",
            example = "改为 8:35")
    @Size(max = 128, message = "补卡备注不能超过 128 个字符")
    private String remark;
}
