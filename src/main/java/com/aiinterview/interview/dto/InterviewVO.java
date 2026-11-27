package com.aiinterview.interview.dto;

import com.aiinterview.interview.entity.Interview;

import java.time.LocalDateTime;

/**
 * 面试会话列表/详情视图。
 *
 * @param id               面试 id
 * @param positionId       岗位 id
 * @param positionTitle    岗位标题（由 job_position 表补充，查不到为空串）
 * @param resumeId         简历 id
 * @param mode             面试模式
 * @param status           面试状态
 * @param totalQuestions   总题数
 * @param currentIndex     已完成答题数
 * @param durationSeconds  面试时长（秒）
 * @param startedAt        开始时间
 * @param finishedAt       结束时间
 * @param createdAt        创建时间
 */
public record InterviewVO(
        Long id,
        Long positionId,
        String positionTitle,
        Long resumeId,
        String mode,
        String status,
        Integer totalQuestions,
        Integer currentIndex,
        Integer durationSeconds,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt) {

    /**
     * 由实体构建视图，岗位标题留空（用于无需展示岗位标题的场景）。
     *
     * @param interview 面试实体
     * @return 面试视图
     */
    public static InterviewVO from(Interview interview) {
        return from(interview, "");
    }

    /**
     * 由实体构建视图，并补充岗位标题。
     *
     * @param interview     面试实体
     * @param positionTitle 岗位标题
     * @return 面试视图
     */
    public static InterviewVO from(Interview interview, String positionTitle) {
        return new InterviewVO(
                interview.getId(),
                interview.getPositionId(),
                positionTitle == null ? "" : positionTitle,
                interview.getResumeId(),
                interview.getMode() == null ? null : interview.getMode().name(),
                interview.getStatus() == null ? null : interview.getStatus().name(),
                interview.getTotalQuestions(),
                interview.getCurrentIndex(),
                interview.getDurationSeconds(),
                interview.getStartedAt(),
                interview.getFinishedAt(),
                interview.getCreatedAt());
    }
}
