package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.config.OpenApiConfig;
import com.example.demo.pojo.dto.AdminResetPasswordRequest;
import com.example.demo.pojo.dto.ChangePasswordRequest;
import com.example.demo.pojo.dto.LoginRequest;
import com.example.demo.pojo.dto.UpdateNicknameRequest;
import com.example.demo.pojo.vo.LoginResult;
import com.example.demo.security.AuthInterceptor;
import com.example.demo.security.TokenStore;
import com.example.demo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证相关接口：登录、当前用户、退出登录、修改密码、修改昵称。
 *
 * <p>路径特意写成 {@code /user/login}，和鸿蒙端 {@code request.post('/user/login', ...)}
 * 完全对齐（客户端会自动拼上 BASE_URL）。</p>
 */
@Tag(name = "用户登录", description = "登录、获取当前用户、退出登录、修改密码、修改昵称")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final TokenStore tokenStore;

    /**
     * 登录。
     *
     * <pre>
     * POST /user/login
     * Content-Type: application/json
     * {"userName":"admin","password":"123456"}
     * </pre>
     *
     * <p>成功返回：{@code {"code":1,"msg":"","data":{"token":"...","userId":1,...}}}</p>
     *
     * <p>{@code @Valid} 触发 LoginRequest 上的注解校验，
     * 校验不过会抛 MethodArgumentNotValidException，被全局异常处理器接住。</p>
     */
    @Operation(summary = "登录", description = "校验账号密码，成功返回 token；账号或密码错误返回 HTTP 401 + code=0")
    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 获取当前登录用户信息（需要 token）。
     *
     * <p>header 里带：{@code Authorization: Bearer {token}}</p>
     */
    @Operation(summary = "获取当前登录用户", description = "需要请求头 Authorization: Bearer {token}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/info")
    public Result<LoginResult> info(@Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                    HttpServletRequest request) {
        LoginResult info = authService.getUserInfo(userId);
        info.setToken(tokenStore.resolveToken(request));
        return Result.success(info);
    }

    /** 退出登录：让当前 token 立刻失效 */
    @Operation(summary = "退出登录", description = "让当前 token 立即失效")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        tokenStore.remove(tokenStore.resolveToken(request));
        return Result.success();
    }

    /**
     * 修改自己的密码。
     *
     * <p>必须带上原密码；修改成功后当前 token 以及该用户其它 token 全部失效，需要重新登录。</p>
     */
    @Operation(summary = "修改自己的密码",
            description = "需要原密码；成功后该用户所有 token 失效，需要重新登录")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PutMapping("/password")
    public Result<Void> changePassword(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return Result.success();
    }

    /**
     * 修改自己的昵称。
     *
     * <p>所有登录用户都可以修改；只更新 nick_name，不会使 token 失效。</p>
     */
    @Operation(summary = "修改自己的昵称",
            description = "所有登录用户都可以调用；只更新昵称，不会使 token 失效，不需要重新登录")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PutMapping("/nickname")
    public Result<Void> updateNickname(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Valid @RequestBody UpdateNicknameRequest request) {
        authService.updateNickName(userId, request);
        return Result.success();
    }

    /**
     * 管理员重置用户密码（包括其他管理员）。
     *
     * <p>普通用户调用返回 403；管理员也可以通过这个接口重置自己的密码，
     * 但如果要校验原密码，请走 {@code PUT /user/password}。</p>
     */
    @Operation(summary = "管理员重置用户密码",
            description = "只有 ADMIN 可以调用；可以重置普通用户、其他管理员或自己；成功后目标用户所有 token 失效")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PutMapping("/password/{userId}")
    public Result<Void> resetPassword(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "目标用户 ID", example = "2") @PathVariable Long userId,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        authService.resetPassword(operatorId, userId, request);
        return Result.success();
    }
}
