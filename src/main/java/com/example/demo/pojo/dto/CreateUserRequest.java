package com.example.demo.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 管理员创建账号的入参。
 */
@Data
@Schema(description = "管理员创建账号请求")
public class CreateUserRequest {

    @Schema(description = "登录账号，不能重复", example = "zhangsan", maxLength = 32,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名长度不能超过 32 个字符")
    private String userName;

    @Schema(description = "初始密码，创建后由用户自己修改", example = "123456", minLength = 6, maxLength = 32,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在 6 到 32 个字符之间")
    @ToString.Exclude
    private String password;

    @Schema(description = "昵称；不传默认“新用户”", example = "张三", maxLength = 32)
    @Size(max = 32, message = "昵称长度不能超过 32 个字符")
    private String nickName;

    @Schema(description = "角色；不传默认 USER", example = "USER",
            allowableValues = {"USER", "ADMIN"})
    @Pattern(regexp = "USER|ADMIN", message = "role 只能是 USER 或 ADMIN")
    private String role;
}
