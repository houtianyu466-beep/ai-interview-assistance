package com.aiinterview.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 知识库配置，对应 {@code app.rag.*}。
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li>{@code enabled}：知识库总开关。关闭后 {@code KnowledgeService#search} 直接返回空列表，
 *         练习流程不受影响；灌库与索引重建仍可用，便于手工维护数据。</li>
 *     <li>{@code chunkSize / chunkOverlap}：分块长度与重叠长度（按字符计，中文友好）。</li>
 *     <li>{@code topK}：检索默认返回条数。</li>
 *     <li>{@link Embedding}：向量化配置。默认 {@code provider=none}，此时只做 BM25 检索；
 *         配置为 {@code openai} 后自动升级为 BM25 + 向量混合检索。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /** 知识库总开关，默认开启 */
    private boolean enabled = true;
    /** 单个分块的目标字符数，默认 600 */
    private int chunkSize = 600;
    /** 相邻分块的重叠字符数，默认 80 */
    private int chunkOverlap = 80;
    /** 检索默认返回条数，默认 5 */
    private int topK = 5;
    /** 向量化配置 */
    private Embedding embedding = new Embedding();

    /** 返回是否启用知识库。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置是否启用知识库。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回分块目标字符数。 */
    public int getChunkSize() {
        return chunkSize;
    }

    /** 设置分块目标字符数。 */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /** 返回分块重叠字符数。 */
    public int getChunkOverlap() {
        return chunkOverlap;
    }

    /** 设置分块重叠字符数。 */
    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    /** 返回检索默认条数。 */
    public int getTopK() {
        return topK;
    }

    /** 设置检索默认条数。 */
    public void setTopK(int topK) {
        this.topK = topK;
    }

    /** 返回向量化配置。 */
    public Embedding getEmbedding() {
        return embedding;
    }

    /** 设置向量化配置。 */
    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    /**
     * 向量化（embedding）配置，对应 {@code app.rag.embedding.*}。
     *
     * <p>只要服务提供 OpenAI 兼容的 {@code POST /embeddings} 接口（OpenAI、SiliconFlow、
     * 通义千问、自建 BGE 服务等），改 {@code base-url} 与 {@code model} 即可接入。</p>
     */
    public static class Embedding {

        /** 提供方：none（默认，只用 BM25） | openai（OpenAI 兼容接口） */
        private String provider = "none";
        /** 接口基础地址，末尾不要带斜杠 */
        private String baseUrl = "https://api.openai.com/v1";
        /** 接口密钥，留空表示未配置 */
        private String apiKey = "";
        /** 向量模型名，如 text-embedding-3-small、bge-m3 */
        private String model = "text-embedding-3-small";

        /** 返回向量化服务提供方。 */
        public String getProvider() {
            return provider;
        }

        /** 设置向量化服务提供方。 */
        public void setProvider(String provider) {
            this.provider = provider;
        }

        /** 返回接口基础地址。 */
        public String getBaseUrl() {
            return baseUrl;
        }

        /** 设置接口基础地址。 */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /** 返回接口密钥。 */
        public String getApiKey() {
            return apiKey;
        }

        /** 设置接口密钥。 */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /** 返回向量模型名。 */
        public String getModel() {
            return model;
        }

        /** 设置向量模型名。 */
        public void setModel(String model) {
            this.model = model;
        }
    }
}
