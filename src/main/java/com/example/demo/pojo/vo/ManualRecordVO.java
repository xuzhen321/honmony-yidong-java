package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理员补卡 / 修改后的记录信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员补卡记录")
public class ManualRecordVO {

    @Schema(description = "打卡记录 ID", example = "88")
    private Long recordId;

    @Schema(description = "考勤任务 ID", example = "501")
    private Long taskId;

    @Schema(description = "员工用户 ID", example = "3")
    private Long userId;

    @Schema(description = "IN 上班 / OUT 下班", example = "IN")
    private String checkType;

    @Schema(description = "考勤日期", example = "2026-10-29")
    private LocalDate recordDate;

    @Schema(description = "实际打卡时间")
    private LocalDateTime checkedAt;

    @Schema(description = "VOICE 声纹打卡 / MANUAL 管理员补卡", example = "MANUAL")
    private String source;

    @Schema(description = "补卡管理员 ID", example = "1")
    private Long operatorId;

    @Schema(description = "补卡备注")
    private String remark;
}
