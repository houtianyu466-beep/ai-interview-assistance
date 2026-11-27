package com.aiinterview.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 知识库检索请求。
 *
 * @param query 查询文本，必填
 * @param topK  返回条数，1-20；为空时使用 {@code app.rag.top-k}
 */
public record KnowledgeSearchRequest(
        @NotBlank(message = "查询内容不能为空")
        String query,
        @Min(value = 1, message = "topK 不能小于 1")
        @Max(value = 20, message = "topK 不能超过 20")
        Integer topK) {
}
