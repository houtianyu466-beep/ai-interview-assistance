package com.aiinterview.interview.entity;

import com.aiinterview.interview.enums.InterviewMode;
import com.aiinterview.interview.enums.InterviewStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试会话，对应表 {@code interview_session}。
 *
 * <p>注意表名不是 {@code interview}，避免与数据库保留字/关键字冲突。</p>
 */
@Data
@TableName("interview_session")
public class Interview {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 候选人 */
    private Long userId;

    private Long positionId;

    private Long resumeId;

    private InterviewMode mode;

    private InterviewStatus status;

    /** 本次面试的总题数（创建时确定） */
    private Integer totalQuestions;

    /** 已完成答题数，同时作为「当前第几题」的游标 */
    private Integer currentIndex;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Integer durationSeconds;

    /** 切屏次数（前端上报，用于反作弊提示） */
    private Integer switchCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
