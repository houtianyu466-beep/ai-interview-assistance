package com.aiinterview.practice.dto;

import java.util.List;
import java.util.Map;

/**
 * 练习复盘视图。
 *
 * <p>面向练习者：不包含任何录用建议，只给出分数、等级、优势短板与可执行的下一步练习建议。</p>
 *
 * @param session         练习会话
 * @param overallScore    综合得分
 * @param dimensionScores 维度均分
 * @param level           等级编码：EXCELLENT / GOOD / IMPROVING / NEEDS_WORK
 * @param levelLabel      等级中文名
 * @param strengths       优势点
 * @param weaknesses      待改进点
 * @param summary         总评
 * @param questions       单题复盘（含参考答案）
 * @param nextSteps       下一步练习建议（最多 4 条）
 */
public record PracticeReviewVO(
        PracticeSessionVO session,
        Double overallScore,
        Map<String, Double> dimensionScores,
        String level,
        String levelLabel,
        List<String> strengths,
        List<String> weaknesses,
        String summary,
        List<PracticeQuestionReviewVO> questions,
        List<String> nextSteps) {
}
