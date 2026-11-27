package com.aiinterview.question.dto;

import com.aiinterview.question.entity.QuestionBank;

import java.time.LocalDateTime;

/**
 * 题库题目展示对象。
 *
 * @param id              题目 id
 * @param positionId      所属岗位 id，为空表示通用题
 * @param category        题目分类
 * @param difficulty      难度 1-5
 * @param content         题干内容
 * @param referenceAnswer 参考答案
 * @param keywords        关键点，逗号分隔
 * @param tags            标签
 * @param source          来源：MANUAL / AI
 * @param createdAt       创建时间
 */
public record QuestionVO(
        Long id,
        Long positionId,
        String category,
        Integer difficulty,
        String content,
        String referenceAnswer,
        String keywords,
        String tags,
        String source,
        LocalDateTime createdAt) {

    /**
     * 由实体转换为展示对象。
     *
     * @param entity 题库实体，允许为 {@code null}
     * @return 展示对象，入参为空时返回 {@code null}
     */
    public static QuestionVO from(QuestionBank entity) {
        if (entity == null) {
            return null;
        }
        return new QuestionVO(
                entity.getId(),
                entity.getPositionId(),
                entity.getCategory(),
                entity.getDifficulty(),
                entity.getContent(),
                entity.getReferenceAnswer(),
                entity.getKeywords(),
                entity.getTags(),
                entity.getSource(),
                entity.getCreatedAt());
    }
}
