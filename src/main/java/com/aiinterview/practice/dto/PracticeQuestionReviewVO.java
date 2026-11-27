package com.aiinterview.practice.dto;

import com.aiinterview.interview.dto.QuestionReviewVO;
import com.aiinterview.interview.entity.InterviewQuestion;

import java.util.Map;

/**
 * 练习复盘中的单题记录。
 *
 * <p>与招聘场景的报告不同，练习场景允许练习者查看参考答案，因此这里额外返回
 * {@code referenceAnswer}，用于对照学习而不是评分。</p>
 *
 * @param seq             题号
 * @param content         题干
 * @param answer          练习者回答（跳过时为「（已跳过）」）
 * @param score           本题得分，跳过题为空
 * @param dimensionScores 维度得分
 * @param comment         点评
 * @param referenceAnswer 参考答案/评分要点
 */
public record PracticeQuestionReviewVO(
        Integer seq,
        String content,
        String answer,
        Double score,
        Map<String, Double> dimensionScores,
        String comment,
        String referenceAnswer) {

    /**
     * 由题目实体构建复盘视图，维度得分 JSON 会被反向解析。
     *
     * @param question 题目实体
     * @return 单题复盘
     */
    public static PracticeQuestionReviewVO from(InterviewQuestion question) {
        return new PracticeQuestionReviewVO(
                question.getSeq(),
                question.getContent(),
                question.getAnswer(),
                question.getScore() == null ? null : question.getScore().doubleValue(),
                QuestionReviewVO.parseDimensions(question.getDimensionScores()),
                question.getComment(),
                question.getReferenceAnswer());
    }
}
