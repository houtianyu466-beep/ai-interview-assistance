package com.aiinterview.system.dto;

import com.aiinterview.system.entity.SysUser;

import java.time.LocalDateTime;

/**
 * 用户视图对象，对外不暴露密码。
 *
 * @param id          用户 id
 * @param username    用户名
 * @param realName    真实姓名
 * @param email       邮箱
 * @param phone       手机号
 * @param role        角色（CANDIDATE / INTERVIEWER / ADMIN）
 * @param status      状态（ACTIVE / DISABLED）
 * @param avatar      头像地址
 * @param lastLoginAt 最近登录时间
 * @param createdAt   创建时间
 */
public record UserVO(Long id,
                     String username,
                     String realName,
                     String email,
                     String phone,
                     String role,
                     String status,
                     String avatar,
                     LocalDateTime lastLoginAt,
                     LocalDateTime createdAt) {

    /**
     * 由实体转换为视图对象。
     *
     * @param user 用户实体
     * @return 用户视图对象，入参为 null 时返回 null
     */
    public static UserVO from(SysUser user) {
        if (user == null) {
            return null;
        }
        String role = user.getRole() == null ? null : user.getRole().name();
        String status = user.getStatus() == null ? null : user.getStatus().name();
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getEmail(),
                user.getPhone(), role, status, user.getAvatar(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
