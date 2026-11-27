package com.aiinterview.video.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建视频房间请求。
 *
 * @param mode              房间模式，仅允许 {@code AI} 或 {@code HUMAN}
 * @param practiceSessionId AI 模式下必填，关联的练习会话 id（{@code interview_session.id}）
 */
public record CreateVideoRoomRequest(
        @NotBlank(message = "mode 不能为空") String mode,
        Long practiceSessionId) {
}
