package com.aiinterview.question.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 题库题目保存请求。创建与更新共用同一份载荷，更新时整份覆盖题目内容。
 *
 * @param positionId      所属岗位 id，为空表示通用题（可被所有岗位复用）
 * @param category        题目分类，例如 JAVA / SPRING / MYSQL / REDIS / ALGORITHM / BEHAVIOR
 * @param difficulty      难度，1-5，数值越大越难
 * @param content         题干内容
 * @param referenceAnswer 参考答案，AI 评分时作为参照
 * @param keywords        关键点，逗号分隔，规则降级评分时使用
 * @param tags            标签，便于检索
 */
public record QuestionSaveRequest(
        Long positionId,
        @NotBlank(message = "题目分类不能为空") String category,
        @NotNull(message = "难度不能为空")
        @Min(value = 1, message = "难度不能小于 1")
        @Max(value = 5, message = "难度不能大于 5") Integer difficulty,
        @NotBlank(message = "题目内容不能为空") String content,
        String referenceAnswer,
        String keywords,
        String tags) {
}
