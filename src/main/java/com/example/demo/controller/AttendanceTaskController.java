package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.config.OpenApiConfig;
import com.example.demo.pojo.dto.CreateAttendanceTaskRequest;
import com.example.demo.pojo.vo.AttendanceTaskDetailVO;
import com.example.demo.pojo.vo.AttendanceTaskListItemVO;
import com.example.demo.pojo.vo.CreateAttendanceTaskResult;
import com.example.demo.pojo.vo.MyAttendanceTaskVO;
import com.example.demo.pojo.vo.PageResult;
import com.example.demo.security.AuthInterceptor;
import com.example.demo.service.AttendanceTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 考勤任务接口：管理员发起任务、查看任务；普通用户查看自己的任务。
 */
@Tag(name = "考勤任务", description = "管理员发起考勤任务、查看任务；用户查看自己的任务")
@RestController
@RequestMapping("/attendance/task")
@RequiredArgsConstructor
public class AttendanceTaskController {

    private final AttendanceTaskService attendanceTaskService;

    @Operation(summary = "管理员创建考勤任务",
            description = "仅管理员；指定 IN/OUT、时间窗口和人员名单，返回 taskId")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping
    public Result<CreateAttendanceTaskResult> create(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Valid @RequestBody CreateAttendanceTaskRequest request) {
        return Result.success(attendanceTaskService.createTask(operatorId, request));
    }

    @Operation(summary = "管理员分页查询考勤任务",
            description = "仅管理员；state 可选 UPCOMING / OPEN / CLOSED，checkType 可选 IN / OUT")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/page")
    public Result<PageResult<AttendanceTaskListItemVO>> page(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "UPCOMING 未开始 / OPEN 进行中 / CLOSED 已结束")
            @RequestParam(required = false) String state,
            @Parameter(description = "IN 上班 / OUT 下班")
            @RequestParam(required = false) String checkType,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long pageNum,
            @Parameter(description = "每页条数，默认 10", example = "10")
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(attendanceTaskService.pageTasks(
                operatorId, state, checkType, pageNum, pageSize));
    }

    @Operation(summary = "用户查看自己的考勤任务",
            description = "当前登录用户；只返回自己所在的任务，state=OPEN && done=false 才能打卡")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/mine")
    public Result<PageResult<MyAttendanceTaskVO>> mine(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long pageNum,
            @Parameter(description = "每页条数，默认 10", example = "10")
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(attendanceTaskService.pageMyTasks(userId, pageNum, pageSize));
    }

    @Operation(summary = "管理员查看考勤任务详情",
            description = "仅管理员；返回任务信息和名单完成情况")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/{taskId}")
    public Result<AttendanceTaskDetailVO> detail(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "任务 ID", example = "501") @PathVariable Long taskId) {
        return Result.success(attendanceTaskService.getTaskDetail(operatorId, taskId));
    }
}
