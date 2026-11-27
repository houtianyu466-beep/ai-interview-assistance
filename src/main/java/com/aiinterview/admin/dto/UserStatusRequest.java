package com.aiinterview.admin.dto;

import com.aiinterview.system.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员修改用户状态的请求。
 *
 * @param status 目标状态，ACTIVE 启用 / DISABLED 禁用
 */
public record UserStatusRequest(@NotNull(message = "用户状态不能为空") UserStatus status) {
}
