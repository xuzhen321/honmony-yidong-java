package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.config.OpenApiConfig;
import com.example.demo.pojo.dto.ManualCheckinRequest;
import com.example.demo.pojo.dto.UpdateManualRecordRequest;
import com.example.demo.pojo.vo.ManualRecordVO;
import com.example.demo.security.AuthInterceptor;
import com.example.demo.service.AttendanceRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员补卡 / 修改补卡 / 撤销补卡。
 */
@Tag(name = "考勤记录管理", description = "管理员为忘记打卡的员工补卡、修改或撤销补卡记录")
@RestController
@RequestMapping("/attendance/record")
@RequiredArgsConstructor
public class AttendanceRecordController {

    private final AttendanceRecordService attendanceRecordService;

    @Operation(summary = "管理员手动补卡",
            description = "仅管理员；员工必须在任务名单内且尚未打卡，任务必须已经开始")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/manual")
    public Result<ManualRecordVO> manual(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Valid @RequestBody ManualCheckinRequest request) {
        return Result.success(attendanceRecordService.manualCheckin(operatorId, request));
    }

    @Operation(summary = "管理员修改补卡记录",
            description = "仅管理员；只能修改 source=MANUAL 的记录，字段为 null 表示不修改")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PutMapping("/{recordId}/manual")
    public Result<ManualRecordVO> updateManual(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "打卡记录 ID", example = "88") @PathVariable Long recordId,
            @Valid @RequestBody UpdateManualRecordRequest request) {
        return Result.success(
                attendanceRecordService.updateManual(operatorId, recordId, request));
    }

    @Operation(summary = "管理员撤销补卡",
            description = "仅管理员；只能撤销 source=MANUAL 的记录，撤销后员工状态回到未打卡")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/{recordId}/revoke")
    public Result<Void> revoke(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "打卡记录 ID", example = "88") @PathVariable Long recordId) {
        attendanceRecordService.revoke(operatorId, recordId);
        return Result.success();
    }
}
