package com.aiinterview.practice.dto;

import java.time.LocalDateTime;

/**
 * 练习会话视图。
 *
 * <p>底层复用 {@code interview_session} 表：{@code targetId} 即该表的 {@code position_id} 字段，
 * {@code currentIndex} 表示已完成（含跳过）的题数。</p>
 *
 * @param id              练习 id
 * @param targetId        练习方向 id
 * @param targetTitle     练习方向名称
 * @param targetSubtitle  练习方向副标题
 * @param mode            练习模式
 * @param status          练习状态
 * @param totalQuestions  总题数
 * @param currentIndex    当前题号游标（已完成题数）
 * @param answeredCount   已作答题数（与 currentIndex 一致，含跳过）
 * @param score           综合得分，未生成报告时为空
 * @param createdAt       创建时间
 * @param finishedAt      结束时间
 * @param durationSeconds 练习时长（秒）
 */
public record PracticeSessionVO(
        Long id,
        Long targetId,
        String targetTitle,
        String targetSubtitle,
        String mode,
        String status,
        Integer totalQuestions,
        Integer currentIndex,
        Integer answeredCount,
        Double score,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        Integer durationSeconds) {
}
