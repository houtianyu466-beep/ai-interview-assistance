package com.aiinterview.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料请求。字段为 null 时表示不修改。
 *
 * @param realName 真实姓名
 * @param email    邮箱
 * @param phone    手机号
 * @param avatar   头像地址
 */
public record UpdateProfileRequest(

        @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
        String realName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        String email,

        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @Size(max = 255, message = "头像地址长度不能超过 255 个字符")
        String avatar
) {
}
