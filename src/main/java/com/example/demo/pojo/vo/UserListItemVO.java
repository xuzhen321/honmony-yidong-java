package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户列表项")
@Data
public class UserListItemVO {

    @Schema(description = "用户 ID", example = "1")
    private Long id;

    @Schema(description = "登录账号", example = "admin")
    private String userName;

    @Schema(description = "昵称", example = "管理员")
    private String nickName;

    @Schema(description = "角色：ADMIN 管理员 / USER 普通用户", example = "ADMIN")
    private String role;

    @Schema(description = "0 正常，1 已冻结", example = "0")
    private Integer deleted;
}
