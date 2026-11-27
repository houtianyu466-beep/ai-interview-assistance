package com.aiinterview.interview.dto;

import java.util.List;
import java.util.Map;

/**
 * 单题作答结果。
 *
 * @param questionId      题目 id
 * @param score           本题得分
 * @param dimensionScores 维度得分
 * @param comment         点评
 * @param hits            答对的关键点
 * @param misses          遗漏的关键点
 * @param finished        本场面试是否已结束
 * @param nextQuestion    下一题，面试结束时为空
 * @param reportId        面试报告 id，仅在 finished=true 时有值
 */
public record AnswerResultVO(
        Long questionId,
        Double score,
        Map<String, Double> dimensionScores,
        String comment,
        List<String> hits,
        List<String> misses,
        boolean finished,
        CurrentQuestionVO nextQuestion,
        Long reportId) {
}
