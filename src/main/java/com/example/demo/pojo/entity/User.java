package com.example.demo.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;


@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {

    /** 数据库自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    @TableField("user_name")
    private String userName;

    /** BCrypt 密文，形如 $2a$10$xxxxx；toString 时排除，避免日志泄露 */
    @TableField("password")
    @ToString.Exclude
    private String password;

    /** 昵称，界面上展示用 */
    @TableField("nick_name")
    private String nickName;

    /** 角色：ADMIN 管理员 / USER 普通用户 */
    @TableField("role")
    private String role;

    /** 逻辑删除：0 正常，1 已删除；MyBatis-Plus 查询会自动过滤掉已删除数据 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
