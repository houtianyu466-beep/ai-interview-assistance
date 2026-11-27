package com.aiinterview.agent.dto;

/**
 * 知识库引用，供前端展示「结论来自哪篇资料」。
 *
 * @param title   知识标题
 * @param snippet 内容摘要，最多 120 字
 * @param score   向量/关键词检索得分
 */
public record CitationVO(String title, String snippet, double score) {
}
