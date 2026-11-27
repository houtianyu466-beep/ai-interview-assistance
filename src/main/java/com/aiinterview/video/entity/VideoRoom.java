package com.aiinterview.video.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频面试房间，对应表 {@code video_room}。
 *
 * <p>房间只保存进入房间所需的元信息，媒体流不经过后端：</p>
 * <ul>
 *     <li>{@code mode=AI}：房主与 AI 进行练习，房间内只允许房主一人；</li>
 *     <li>{@code mode=HUMAN}：真人双人面试，房间内最多两人。</li>
 * </ul>
 */
@Data
@TableName("video_room")
public class VideoRoom {

    /** 模式：AI 练习。 */
    public static final String MODE_AI = "AI";
    /** 模式：真人对练。 */
    public static final String MODE_HUMAN = "HUMAN";
    /** 状态：等待中，可进入。 */
    public static final String STATUS_WAITING = "WAITING";
    /** 状态：已结束，不可再进入。 */
    public static final String STATUS_ENDED = "ENDED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 8 位房间码，大写字母 + 数字，排除易混淆字符 0/O/1/I */
    private String code;

    /** 房间模式：AI | HUMAN */
    private String mode;

    /** AI 模式下关联的练习会话 id（interview_session.id），真人对练可为空 */
    private Long practiceSessionId;

    /** 房主用户 id */
    private Long hostUserId;

    /** 房间状态：WAITING | ENDED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 结束时间，未结束时为 {@code null} */
    private LocalDateTime endedAt;
}
