package com.aiinterview.system.dto;

/**
 * 登录（或注册）成功后返回的令牌与用户信息。
 *
 * @param accessToken      JWT 访问令牌
 * @param tokenType        令牌类型，固定为 Bearer
 * @param expiresInSeconds 令牌有效期（秒）
 * @param user             当前用户信息
 */
public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserVO user) {
}
