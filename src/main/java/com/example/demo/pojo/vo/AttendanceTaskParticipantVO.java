package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "考勤任务名单成员")
@Data
public class AttendanceTaskParticipantVO {

    @Schema(description = "用户 ID", example = "2")
    private Long userId;

    @Schema(description = "登录账号", example = "zhangsan")
    private String userName;

    @Schema(description = "昵称", example = "张三")
    private String nickName;

    @Schema(description = "PENDING 待打卡 / DONE 已完成", example = "DONE")
    private String status;

    @Schema(description = "完成打卡时间")
    private LocalDateTime checkedAt;

    @Schema(description = "打卡记录 ID", example = "88301")
    private Long recordId;
}
