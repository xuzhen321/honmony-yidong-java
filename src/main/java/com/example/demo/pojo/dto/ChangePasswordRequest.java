package com.example.demo.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Schema(description = "修改自己的密码")
@Data
public class ChangePasswordRequest {

    @Schema(description = "原密码", example = "123456", minLength = 6, maxLength = 32,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "原密码不能为空")
    @Size(min = 6, max = 32, message = "原密码长度必须在 6 到 32 个字符之间")
    @ToString.Exclude
    private String oldPassword;

    @Schema(description = "新密码", example = "654321", minLength = 6, maxLength = 32,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度必须在 6 到 32 个字符之间")
    @ToString.Exclude
    private String newPassword;
}
