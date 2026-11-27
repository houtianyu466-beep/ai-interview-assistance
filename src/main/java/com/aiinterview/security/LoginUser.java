package com.aiinterview.security;

/**
 * 登录用户，作为 Spring Security 的 principal 放入 SecurityContext。
 *
 * @param id       用户 id
 * @param username 用户名
 * @param role     角色
 */
public record LoginUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isInterviewer() {
        return "INTERVIEWER".equalsIgnoreCase(role);
    }
}
