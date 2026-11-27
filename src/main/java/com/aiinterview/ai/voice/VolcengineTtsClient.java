package com.aiinterview.ai.voice;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 火山引擎（豆包）语音合成实现（{@code app.tts.provider=volcengine}）。
 *
 * <p>为什么单独实现：豆包的韵律与停顿明显比浏览器 speechSynthesis 自然，
 * 但它不是 OpenAI 协议，需要 appid + access token + cluster 三元组，
 * 且返回的是 base64 音频。</p>
 *
 * <p>接口：{@code POST https://openspeech.bytedance.com/api/v1/tts}，
 * Header {@code Authorization: Bearer;{access_token}}，
 * 返回 {@code {code:3000, data:"<base64 音频>"}}。</p>
 */
@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "volcengine")
public class VolcengineTtsClient implements TtsClient {

    private static final Logger log = LoggerFactory.getLogger(VolcengineTtsClient.class);

    /** 豆包 HTTP 合成固定地址 */
    private static final String ENDPOINT = "https://openspeech.bytedance.com/api/v1/tts";
    /** 成功码 */
    private static final int SUCCESS_CODE = 3000;

    private final TtsProperties properties;
    private final HttpClient httpClient;

    /**
     * 构造器注入。
     *
     * @param properties 合成配置
     */
    public VolcengineTtsClient(TtsProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    /**
     * 调用豆包合成接口。
     *
     * @param text  文本
     * @param voice 音色，空时用配置默认值
     * @return 合成结果
     */
    @Override
    public TtsResult synthesize(String text, String voice) {
        if (!enabled()) {
            throw new BizException(ErrorCode.AI_ERROR,
                    "语音合成未配置：请设置 APP_TTS_APP_ID 与 APP_TTS_ACCESS_TOKEN");
        }
        long start = System.currentTimeMillis();
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("appid", properties.getAppId());
        app.put("token", properties.getAccessToken());
        app.put("cluster", TextUtils.defaultIfBlank(properties.getCluster(), "volcano_tts"));

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", "ai-interview");

        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("voice_type", TextUtils.defaultIfBlank(voice, properties.getVolcVoice()));
        audio.put("encoding", "mp3");
        audio.put("speed_ratio", properties.getSpeed() <= 0 ? 1.0 : properties.getSpeed());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reqid", UUID.randomUUID().toString());
        request.put("text", text);
        request.put("operation", "query");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", app);
        body.put("user", user);
        body.put("audio", audio);
        body.put("request", request);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer;" + properties.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtils.mapper().writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new BizException(ErrorCode.AI_ERROR,
                        "语音合成失败：HTTP " + response.statusCode());
            }
            JsonNode root = JsonUtils.mapper().readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code != SUCCESS_CODE) {
                throw new BizException(ErrorCode.AI_ERROR,
                        "语音合成失败：code=" + code + " message=" + root.path("message").asText(""));
            }
            String data = root.path("data").asText("");
            if (TextUtils.isBlank(data)) {
                throw new BizException(ErrorCode.AI_ERROR, "语音合成返回空音频");
            }
            byte[] audioBytes = Base64.getDecoder().decode(data);
            long latency = System.currentTimeMillis() - start;
            log.debug("豆包语音合成完成 voice={} bytes={} latency={}ms",
                    audio.get("voice_type"), audioBytes.length, latency);
            return new TtsResult(audioBytes, "audio/mpeg", providerName(), latency);
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AI_ERROR, "语音合成被中断", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.AI_ERROR, "语音合成调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * appid 与 token 都配置时视为可用。
     *
     * @return 是否可用
     */
    @Override
    public boolean enabled() {
        return !TextUtils.isBlank(properties.getAppId()) && !TextUtils.isBlank(properties.getAccessToken());
    }

    /**
     * 返回提供方标识。
     *
     * @return volcengine
     */
    @Override
    public String providerName() {
        return "volcengine";
    }
}
