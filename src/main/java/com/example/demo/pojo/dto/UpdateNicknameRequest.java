package com.example.demo.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "修改昵称请求体")
@Data
public class UpdateNicknameRequest {

    @Schema(description = "新昵称", example = "张三", maxLength = 32,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称长度不能超过 32 个字符")
    private String nickName;
}
