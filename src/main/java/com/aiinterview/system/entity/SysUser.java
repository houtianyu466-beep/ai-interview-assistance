package com.aiinterview.system.entity;

import com.aiinterview.system.enums.UserRole;
import com.aiinterview.system.enums.UserStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 {@code sys_user}。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    /** BCrypt 加密后的密码，不参与 JSON 序列化 */
    @JsonIgnore
    private String password;

    private String realName;

    private String email;

    private String phone;

    private UserRole role;

    private UserStatus status;

    private String avatar;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
