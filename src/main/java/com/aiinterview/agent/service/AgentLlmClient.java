package com.aiinterview.agent.service;

import com.aiinterview.ai.LlmProperties;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 面试教练专用的 OpenAI 兼容客户端。
 *
 * <p>与 {@code OpenAiCompatibleLlmClient} 的区别：教练需要 function calling，
 * 因此请求体额外携带 {@code tools} 与 {@code tool_choice=auto}，并原样返回解析后的响应根节点，
 * 由 {@link AgentService} 负责读取 {@code choices[0].message.content} 与 {@code tool_calls}。</p>
 *
 * <p>超时统一取 {@code app.ai.timeout-seconds}（默认 60 秒）。把 HTTP 调用收敛在这个类里，
 * 单元测试可以 mock 它而不联网。</p>
 */
@Component
public class AgentLlmClient {

    private static final Logger log = LoggerFactory.getLogger(AgentLlmClient.class);

    /** 兜底超时：配置缺失或非法时使用 60 秒。 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final LlmProperties properties;
    private final RestClient restClient;

    /**
     * 构造器注入，并按配置初始化带超时的 RestClient。
     *
     * @param properties 大模型配置
     */
    public AgentLlmClient(LlmProperties properties) {
        this.properties = properties;
        int timeoutSeconds = properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(TextUtils.defaultIfBlank(properties.getBaseUrl(), "https://api.openai.com/v1"))
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 调用 {@code POST {base-url}/chat/completions} 并返回解析后的 JSON 根节点。
     *
     * <p>调用方需要读取：{@code choices[0].message.content}、{@code choices[0].message.tool_calls}、
     * {@code usage}。任何网络/解析异常都包装成 {@link IllegalStateException} 抛出，
     * 由 {@link AgentService} 统一降级。</p>
     *
     * @param messages 已按 OpenAI 协议拼好的消息列表
     * @param tools    工具定义（可为空）
     * @return 响应 JSON 根节点
     */
    public JsonNode chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (TextUtils.isBlank(properties.getApiKey())) {
            throw new IllegalStateException("未配置 app.ai.api-key，无法调用真实模型");
        }
        long start = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        body.put("temperature", properties.getTemperature());
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
            log.debug("AI 教练模型调用完成 model={} latency={}ms",
                    properties.getModel(), System.currentTimeMillis() - start);
            return root;
        } catch (Exception e) {
            throw new IllegalStateException("调用面试教练模型失败: " + e.getMessage(), e);
        }
    }
}
