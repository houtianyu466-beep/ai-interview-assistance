package com.aiinterview.knowledge.dto;

/**
 * 知识库检索命中结果。
 *
 * <p>{@code chunkId} / {@code documentId} 使用字符串，避免雪花 id 在 JavaScript 端丢精度；
 * {@code content} 是命中的分块原文，不是整篇文档；{@code score} 为真实计算出的相关性分数
 * （纯 BM25 模式为原始 BM25 分，混合检索模式为 0.65 × 归一化 BM25 + 0.35 × 余弦）。</p>
 *
 * @param chunkId    分块 id（字符串形式）
 * @param documentId 文档 id（字符串形式）
 * @param title      文档标题
 * @param category   文档分类，可能为空
 * @param content    命中的分块正文
 * @param score      相关性分数
 * @param source     文档来源，如 MANUAL
 */
public record KnowledgeHitVO(
        String chunkId,
        String documentId,
        String title,
        String category,
        String content,
        double score,
        String source) {
}
