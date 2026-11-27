package com.aiinterview.video.dto;

import com.aiinterview.video.entity.VideoRoom;

import java.time.LocalDateTime;

/**
 * 视频房间视图，供 HTTP 接口与前端展示使用。
 *
 * @param id                房间 id
 * @param code              8 位房间码
 * @param mode              房间模式：AI | HUMAN
 * @param practiceSessionId AI 模式关联的练习会话 id，可为空
 * @param hostUserId        房主用户 id
 * @param status            房间状态：WAITING | ENDED
 * @param createdAt         创建时间
 */
public record VideoRoomVO(
        Long id,
        String code,
        String mode,
        Long practiceSessionId,
        Long hostUserId,
        String status,
        LocalDateTime createdAt) {

    /**
     * 由房间实体构建视图。
     *
     * @param room 房间实体
     * @return 房间视图
     */
    public static VideoRoomVO from(VideoRoom room) {
        return new VideoRoomVO(
                room.getId(),
                room.getCode(),
                room.getMode(),
                room.getPracticeSessionId(),
                room.getHostUserId(),
                room.getStatus(),
                room.getCreatedAt());
    }
}
