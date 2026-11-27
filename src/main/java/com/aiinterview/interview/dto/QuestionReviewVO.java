package com.aiinterview.interview.dto;

import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.interview.entity.InterviewQuestion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报告中的单题复盘。
 *
 * @param seq             题号
 * @param content         题干
 * @param answer          候选人回答
 * @param score           本题得分
 * @param dimensionScores 维度得分
 * @param comment         点评
 */
public record QuestionReviewVO(
        Integer seq,
        String content,
        String answer,
        Double score,
        Map<String, Double> dimensionScores,
        String comment) {

    /**
     * 由题目实体构建复盘视图，维度得分 JSON 会被反向解析。
     *
     * @param question 题目实体
     * @return 单题复盘
     */
    public static QuestionReviewVO from(InterviewQuestion question) {
        return new QuestionReviewVO(
                question.getSeq(),
                question.getContent(),
                question.getAnswer(),
                question.getScore() == null ? null : question.getScore().doubleValue(),
                parseDimensions(question.getDimensionScores()),
                question.getComment());
    }

    /**
     * 解析维度得分 JSON 字符串。
     *
     * @param json 维度得分 JSON
     * @return 维度得分，解析失败返回空 Map
     */
    public static Map<String, Double> parseDimensions(String json) {
        Map<String, Object> raw = JsonUtils.toMap(json);
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            Double number = toDouble(value);
            if (number != null) {
                result.put(key, number);
            }
        });
        return result;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
