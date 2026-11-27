package com.aiinterview.knowledge.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 未配置向量化时的空实现（{@code app.rag.embedding.provider=none}，默认）。
 *
 * <p>它让检索稳定停留在纯 BM25 模式：{@link #enabled()} 固定为 {@code false}，
 * 灌库与检索都不会发起任何外部请求。</p>
 */
@Component
@ConditionalOnProperty(name = "app.rag.embedding.provider", havingValue = "none", matchIfMissing = true)
public class NoopEmbeddingClient implements EmbeddingClient {

    /**
     * 空实现：不调用任何外部服务。
     *
     * @param text 待向量化文本
     * @return 固定返回空列表
     */
    @Override
    public List<Double> embed(String text) {
        return List.of();
    }

    /**
     * 固定返回未启用。
     *
     * @return {@code false}
     */
    @Override
    public boolean enabled() {
        return false;
    }

    /**
     * 返回固定标识，表示当前没有向量模型参与检索。
     *
     * @return 固定返回 {@code none}
     */
    @Override
    public String model() {
        return "none";
    }
}
