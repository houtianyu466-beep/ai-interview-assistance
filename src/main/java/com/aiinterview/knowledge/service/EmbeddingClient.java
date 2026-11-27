package com.aiinterview.knowledge.service;

import java.util.List;

/**
 * 文本向量化客户端抽象。
 *
 * <p>为什么抽象：默认 {@code provider=none} 时使用 {@link NoopEmbeddingClient}，
 * 只用 BM25 检索；配置成 {@code openai} 后替换为 {@link OpenAiEmbeddingClient}，
 * 检索自动升级为 BM25 + 向量混合，业务代码无需感知。</p>
 */
public interface EmbeddingClient {

    /**
     * 把一段文本转成向量。
     *
     * @param text 待向量化文本
     * @return 向量；未启用或文本为空时返回空列表
     */
    List<Double> embed(String text);

    /**
     * 是否真正启用了向量化。
     *
     * @return 启用返回 {@code true}
     */
    boolean enabled();

    /**
     * 当前向量模型名，用于统计展示。
     *
     * @return 模型名，未启用时返回 {@code none}
     */
    String model();
}
