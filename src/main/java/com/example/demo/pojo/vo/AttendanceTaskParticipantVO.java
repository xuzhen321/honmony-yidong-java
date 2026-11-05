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

    @Schema(description = "VOICE 声纹打卡 / MANUAL 管理员补卡；未打卡为 null")
    private String source;

    @Schema(description = "补卡管理员 ID；声纹打卡或未打卡为 null")
    private Long operatorId;

    @Schema(description = "补卡备注；声纹打卡或未打卡为 null")
    private String remark;
}
