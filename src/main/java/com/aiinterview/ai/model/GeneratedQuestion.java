package com.aiinterview.ai.model;

import java.util.List;

/**
 * AI 生成的单道面试题。
 *
 * @param content         题干
 * @param category        分类（JAVA / SPRING / MYSQL ...）
 * @param difficulty      难度 1-5
 * @param referenceAnswer 参考答案/评分要点
 * @param keywords        关键点，用于规则评分
 */
public record GeneratedQuestion(
        String content,
        String category,
        String difficulty,
        String referenceAnswer,
        List<String> keywords) {

    public GeneratedQuestion {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
