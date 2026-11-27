package com.aiinterview.ai.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生成的面试总评（落库前会被 InterviewReport 实体承载）。
 */
public record InterviewReportDraft(
        double overallScore,
        Map<String, Double> dimensionScores,
        List<String> strengths,
        List<String> weaknesses,
        String suggestion,
        String summary) {

    public InterviewReportDraft {
        overallScore = Math.max(0, Math.min(100, overallScore));
        dimensionScores = dimensionScores == null ? Map.of() : new LinkedHashMap<>(dimensionScores);
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        weaknesses = weaknesses == null ? List.of() : List.copyOf(weaknesses);
    }
}
