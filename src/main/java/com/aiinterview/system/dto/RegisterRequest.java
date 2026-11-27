package com.aiinterview.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>注册入口只开放给候选人（CANDIDATE），面试官与管理员账号由管理员在后台创建。</p>
 *
 * @param username 用户名，3-32 位字母、数字或下划线
 * @param password 明文密码，服务端使用 BCrypt 加密后存储
 * @param realName 真实姓名，可为空
 * @param email    邮箱，可为空
 * @param phone    手机号，可为空
 */
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度必须在 3-32 个字符之间")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度必须在 6-64 个字符之间")
        String password,

        @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
        String realName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        String email,

        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone
) {
}
