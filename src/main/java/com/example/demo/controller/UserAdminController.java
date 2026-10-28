package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.config.OpenApiConfig;
import com.example.demo.pojo.vo.PageResult;
import com.example.demo.pojo.vo.UserListItemVO;
import com.example.demo.security.AuthInterceptor;
import com.example.demo.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理接口：分页查询、冻结、恢复。
 */
@Tag(name = "用户管理", description = "管理员分页查询、冻结、恢复账号")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "分页查询用户",
            description = "仅管理员；userName 模糊匹配，deleted=0 正常 / 1 已冻结，不传返回全部")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/page")
    public Result<PageResult<UserListItemVO>> page(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "用户名，模糊匹配", example = "admin")
            @RequestParam(required = false) String userName,
            @Parameter(description = "0 正常，1 已冻结；不传返回全部", example = "0")
            @RequestParam(required = false) Integer deleted,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long pageNum,
            @Parameter(description = "每页条数，默认 10", example = "10")
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(userAdminService.pageUsers(operatorId, userName, deleted, pageNum, pageSize));
    }

    @Operation(summary = "冻结用户", description = "仅管理员；逻辑删除，返回 data=null；不能冻结自己")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @DeleteMapping("/{userId}")
    public Result<Void> freeze(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "要冻结的用户 ID", example = "2") @PathVariable Long userId) {
        userAdminService.freezeUser(operatorId, userId);
        return Result.success();
    }

    @Operation(summary = "恢复用户", description = "仅管理员；把 deleted 改回 0，返回 data=null")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PutMapping("/{userId}/restore")
    public Result<Void> restore(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "要恢复的用户 ID", example = "2") @PathVariable Long userId) {
        userAdminService.restoreUser(operatorId, userId);
        return Result.success();
    }
}
