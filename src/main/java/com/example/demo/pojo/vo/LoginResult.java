package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Schema(description = "登录成功返回 / 当前登录用户信息")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    /** 登录凭证，之后请求放进 header：Authorization: Bearer {token} */
    @Schema(description = "登录凭证，后续请求放在请求头 Authorization: Bearer {token}",
            example = "097309c4321a406a93e2914c54083cf7")
    private String token;

    @Schema(description = "用户 ID", example = "1")
    private Long userId;

    @Schema(description = "登录账号", example = "admin")
    private String userName;

    @Schema(description = "昵称", example = "管理员")
    private String nickName;

    @Schema(description = "角色：ADMIN 管理员 / USER 普通用户", example = "ADMIN")
    private String role;

    @Schema(description = "是否需要先完成声纹注册：10 个数字未录齐时为 true", example = "false")
    private Boolean needVoiceprintEnroll;
}
