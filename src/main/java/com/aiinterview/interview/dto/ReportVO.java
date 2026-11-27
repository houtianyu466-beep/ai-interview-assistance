package com.aiinterview.interview.dto;

import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.interview.entity.InterviewReport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试报告视图。落库的 JSON 字符串在此处反向解析为集合结构。
 *
 * @param id              报告 id
 * @param interviewId     面试 id
 * @param overallScore    整体得分
 * @param dimensionScores 维度均分
 * @param strengths       优势点
 * @param weaknesses      待改进点
 * @param suggestion      录用建议：PASS / HOLD / REJECT
 * @param summary         总评
 * @param createdAt       生成时间
 * @param questions       单题复盘列表（按题号升序）
 */
public record ReportVO(
        Long id,
        Long interviewId,
        Double overallScore,
        Map<String, Double> dimensionScores,
        List<String> strengths,
        List<String> weaknesses,
        String suggestion,
        String summary,
        LocalDateTime createdAt,
        List<QuestionReviewVO> questions) {

    /**
     * 由报告实体与单题复盘列表构建视图。
     *
     * @param report    报告实体
     * @param questions 单题复盘列表
     * @return 报告视图
     */
    public static ReportVO from(InterviewReport report, List<QuestionReviewVO> questions) {
        return new ReportVO(
                report.getId(),
                report.getInterviewId(),
                report.getOverallScore() == null ? null : report.getOverallScore().doubleValue(),
                QuestionReviewVO.parseDimensions(report.getDimensionScores()),
                JsonUtils.toStringList(report.getStrengths()),
                JsonUtils.toStringList(report.getWeaknesses()),
                report.getSuggestion() == null ? null : report.getSuggestion().name(),
                report.getSummary(),
                report.getCreatedAt(),
                questions == null ? List.of() : List.copyOf(questions));
    }

}
