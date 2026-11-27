package com.aiinterview.ai;

import com.aiinterview.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的客户端实现。
 *
 * <p>适配 DeepSeek、通义千问、Moonshot、vLLM 等所有兼容 {@code /chat/completions} 的服务，
 * 只需改 {@code app.ai.base-url} 与 {@code app.ai.model}。</p>
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final LlmProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleLlmClient(LlmProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public LlmResponse chat(String scene, List<LlmMessage> messages, double temperature) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置 app.ai.api-key，无法调用真实模型");
        }
        long start = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", false);

        try {
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = JsonUtils.mapper().readTree(raw);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.isMissingNode() ? "" : contentNode.asText();
            JsonNode usage = root.path("usage");
            Integer promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
            Integer completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null;
            String model = root.path("model").asText(properties.getModel());
            long latency = System.currentTimeMillis() - start;
            log.debug("AI 调用完成 scene={} model={} latency={}ms", scene, model, latency);
            return new LlmResponse(content, model, promptTokens, completionTokens, latency);
        } catch (Exception e) {
            log.warn("AI 调用失败 scene={} baseUrl={} error={}", scene, properties.getBaseUrl(), e.getMessage());
            throw new IllegalStateException("调用大模型失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }
}
