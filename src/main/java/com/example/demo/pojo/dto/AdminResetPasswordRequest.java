package com.example.demo.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 管理员重置用户密码的请求体。
 *
 * <p>目标用户 ID 不在请求体里，而是放在 URL 路径 {@code /user/password/{userId}}；
 * 操作人 ID 从 token 里取。请求体只负责新密码。</p>
 */
@Schema(description = "管理员重置用户密码；目标用户 ID 在 URL 路径 /user/password/{userId} 中")
@Data
public class AdminResetPasswordRequest {

    @Schema(description = "新密码", example = "654321", minLength = 6, maxLength = 32,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度必须在 6 到 32 个字符之间")
    @ToString.Exclude
    private String newPassword;
}
