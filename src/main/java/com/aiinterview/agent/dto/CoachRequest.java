package com.aiinterview.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 教练对话请求。
 *
 * @param message 用户问题，不能为空，最多 1000 字
 */
public record CoachRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 1000, message = "问题不能超过 1000 字")
        String message) {
}
