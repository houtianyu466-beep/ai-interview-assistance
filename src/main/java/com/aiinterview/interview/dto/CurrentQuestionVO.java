package com.aiinterview.interview.dto;

import com.aiinterview.interview.entity.InterviewQuestion;

/**
 * 当前待作答的题目。
 *
 * @param questionId      题目 id
 * @param seq             题号，从 1 开始
 * @param totalQuestions  本次面试总题数
 * @param content         题干
 * @param category        分类
 * @param difficulty      难度
 * @param referenceAnswer 参考答案（口述评分要点）
 */
public record CurrentQuestionVO(
        Long questionId,
        Integer seq,
        Integer totalQuestions,
        String content,
        String category,
        Integer difficulty,
        String referenceAnswer) {

    /**
     * 由题目实体构建当前题视图。
     *
     * @param question       题目实体
     * @param totalQuestions 总题数
     * @return 当前题视图
     */
    public static CurrentQuestionVO from(InterviewQuestion question, Integer totalQuestions) {
        return new CurrentQuestionVO(
                question.getId(),
                question.getSeq(),
                totalQuestions,
                question.getContent(),
                question.getCategory(),
                question.getDifficulty(),
                question.getReferenceAnswer());
    }
}
