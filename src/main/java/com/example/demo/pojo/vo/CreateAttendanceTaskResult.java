package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "创建考勤任务结果")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAttendanceTaskResult {

    @Schema(description = "任务 ID", example = "501")
    private Long taskId;

    @Schema(description = "名单人数", example = "3")
    private Integer participantCount;
}
