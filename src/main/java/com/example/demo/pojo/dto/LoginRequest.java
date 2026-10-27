package com.example.demo.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;


@Schema(description = "登录请求体")
@Data
public class LoginRequest {

    @Schema(description = "登录账号", example = "admin", maxLength = 32, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名长度不能超过 32 个字符")
    private String userName;

    @Schema(description = "登录密码", example = "123456", minLength = 6, maxLength = 32,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在 6 到 32 个字符之间")
    @ToString.Exclude
    private String password;
}
