package com.aiinterview.security;

import java.time.Instant;

/**
 * 解析后的 JWT 载荷。
 *
 * @param userId    用户 id
 * @param username  用户名
 * @param role      角色（CANDIDATE / INTERVIEWER / ADMIN）
 * @param jti       令牌唯一 id，用于登出时加入黑名单
 * @param expiresAt 过期时间
 */
public record TokenPayload(Long userId, String username, String role, String jti, Instant expiresAt) {
}
