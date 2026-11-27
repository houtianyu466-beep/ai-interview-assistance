package com.aiinterview.interview.dto;

import com.aiinterview.interview.entity.InterviewQuestion;

/**
 * 面试题概要（详情页列表使用，不含参考答案）。
 *
 * @param id         题目 id
 * @param seq        题号
 * @param content    题干
 * @param category   分类
 * @param difficulty 难度
 * @param answer     候选人回答，未作答为空
 * @param score      本题得分，未评分为空
 * @param comment    点评
 */
public record QuestionBriefVO(
        Long id,
        Integer seq,
        String content,
        String category,
        Integer difficulty,
        String answer,
        Double score,
        String comment) {

    /**
     * 由题目实体构建概要视图。
     *
     * @param question 题目实体
     * @return 题目概要
     */
    public static QuestionBriefVO from(InterviewQuestion question) {
        return new QuestionBriefVO(
                question.getId(),
                question.getSeq(),
                question.getContent(),
                question.getCategory(),
                question.getDifficulty(),
                question.getAnswer(),
                question.getScore() == null ? null : question.getScore().doubleValue(),
                question.getComment());
    }
}
