package com.aiinterview.ai.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单题评分结果。
 *
 * @param score      总分 0-100
 * @param dimensions 维度得分，键为 accuracy / depth / expression / structure
 * @param comment    点评
 * @param hits       答对的关键点
 * @param misses     遗漏的关键点
 */
public record AnswerEvaluation(
        double score,
        Map<String, Double> dimensions,
        String comment,
        List<String> hits,
        List<String> misses) {

    public AnswerEvaluation {
        score = clamp(score);
        dimensions = dimensions == null ? Map.of() : new LinkedHashMap<>(dimensions);
        hits = hits == null ? List.of() : List.copyOf(hits);
        misses = misses == null ? List.of() : List.copyOf(misses);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
