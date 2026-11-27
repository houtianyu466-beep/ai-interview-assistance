package com.aiinterview.knowledge.service;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.knowledge.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的向量化实现（{@code app.rag.embedding.provider=openai}）。
 *
 * <p>适配所有兼容 {@code POST /embeddings} 的服务：OpenAI、SiliconFlow、通义千问、
 * 自建 BGE / M3E 服务等，只需改 {@code app.rag.embedding.base-url} 与
 * {@code app.rag.embedding.model}。请求体为 {@code {"model": "...", "input": "..."}}，
 * 响应取 {@code data[0].embedding}。连接与读取超时均为 30 秒。</p>
 *
 * <p>失败语义：网络异常、鉴权失败、响应缺少 embedding 等一律抛
 * {@link ErrorCode#AI_ERROR}，由 {@link KnowledgeService} 在灌库与检索两处分别降级
 * （灌库时该分块向量为 null；检索时退化为纯 BM25），不影响主流程。</p>
 */
@Component
@ConditionalOnProperty(name = "app.rag.embedding.provider", havingValue = "openai")
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

    /** 连接与读取超时（秒） */
    private static final int TIMEOUT_SECONDS = 30;

    private final RagProperties properties;
    private final RestClient restClient;

    /**
     * 构造器注入，同时按配置构建带超时的 RestClient。
     *
     * @param properties RAG 配置
     */
    public OpenAiEmbeddingClient(RagProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getEmbedding().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 调用 {@code /embeddings} 生成文本向量。
     *
     * @param text 待向量化文本，为空时返回空列表
     * @return 向量
     * @throws BizException 未配置密钥或调用失败时抛出 {@link ErrorCode#AI_ERROR}
     */
    @Override
    public List<Double> embed(String text) {
        if (TextUtils.isBlank(text)) {
            return List.of();
        }
        String apiKey = properties.getEmbedding().getApiKey();
        if (TextUtils.isBlank(apiKey)) {
            throw new BizException(ErrorCode.AI_ERROR,
                    "向量化未配置：请设置 APP_EMBEDDING_API_KEY（或 app.rag.embedding.api-key）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getEmbedding().getModel());
        body.put("input", text);
        try {
            String raw = restClient.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = JsonUtils.mapper().readTree(raw);
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new BizException(ErrorCode.AI_ERROR, "向量化接口未返回 embedding");
            }
            List<Double> vector = new ArrayList<>(embeddingNode.size());
            for (JsonNode value : embeddingNode) {
                vector.add(value.asDouble());
            }
            log.debug("向量化完成 model={} dimensions={}", properties.getEmbedding().getModel(), vector.size());
            return vector;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("向量化调用失败 baseUrl={} model={} error={}",
                    properties.getEmbedding().getBaseUrl(), properties.getEmbedding().getModel(), e.getMessage());
            throw new BizException(ErrorCode.AI_ERROR, "向量化调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 固定返回已启用。
     *
     * @return {@code true}
     */
    @Override
    public boolean enabled() {
        return true;
    }

    /**
     * 返回当前向量模型名。
     *
     * @return 模型名
     */
    @Override
    public String model() {
        return properties.getEmbedding().getModel();
    }
}
