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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI 兼容协议的语音合成实现（{@code app.tts.provider=openai}）。
 *
 * <p>适配所有兼容 {@code POST /audio/speech} 的服务：OpenAI tts-1、硅基流动
 * CosyVoice2、各类 OpenAI 代理。请求体：{@code {model, input, voice, response_format, speed}}，
 * 响应体直接是音频字节。</p>
 */
@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "openai")
public class OpenAiTtsClient implements TtsClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTtsClient.class);

    private final TtsProperties properties;
    private final HttpClient httpClient;

    /**
     * 构造器注入。
     *
     * @param properties 合成配置
     */
    public OpenAiTtsClient(TtsProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    /**
     * 调用 {@code /audio/speech} 合成。
     *
     * @param text  文本
     * @param voice 音色，空时用配置默认值
     * @return 合成结果
     */
    @Override
    public TtsResult synthesize(String text, String voice) {
        if (TextUtils.isBlank(properties.getApiKey())) {
            throw new BizException(ErrorCode.AI_ERROR, "语音合成未配置：请设置 APP_TTS_API_KEY");
        }
        long start = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", text);
        body.put("voice", TextUtils.defaultIfBlank(voice, properties.getVoice()));
        body.put("response_format", TextUtils.defaultIfBlank(properties.getResponseFormat(), "mp3"));
        if (properties.getSpeed() > 0 && Math.abs(properties.getSpeed() - 1.0) > 0.01) {
            body.put("speed", properties.getSpeed());
        }
        String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/audio/speech"))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtils.mapper().writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new BizException(ErrorCode.AI_ERROR,
                        "语音合成失败：HTTP " + response.statusCode() + " " + preview(response.body()));
            }
            byte[] audio = response.body();
            if (audio == null || audio.length == 0) {
                throw new BizException(ErrorCode.AI_ERROR, "语音合成返回空音频");
            }
            long latency = System.currentTimeMillis() - start;
            log.debug("语音合成完成 model={} voice={} bytes={} latency={}ms",
                    properties.getModel(), body.get("voice"), audio.length, latency);
            return new TtsResult(audio, contentType(), providerName(), latency);
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AI_ERROR, "语音合成被中断", e);
        } catch (IOException e) {
            throw new BizException(ErrorCode.AI_ERROR, "语音合成调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 已配置 Key 时视为可用。
     *
     * @return 是否可用
     */
    @Override
    public boolean enabled() {
        return !TextUtils.isBlank(properties.getApiKey());
    }

    /**
     * 返回提供方标识。
     *
     * @return openai
     */
    @Override
    public String providerName() {
        return "openai";
    }

    /**
     * 根据输出格式推断 MIME。
     *
     * @return MIME
     */
    private String contentType() {
        String format = properties.getResponseFormat() == null ? "mp3" : properties.getResponseFormat().toLowerCase();
        return switch (format) {
            case "wav" -> "audio/wav";
            case "opus" -> "audio/opus";
            case "pcm" -> "audio/pcm";
            default -> "audio/mpeg";
        };
    }

    /**
     * 截断错误响应，避免把超长内容返回给前端。
     *
     * @param bytes 响应体
     * @return 可读片段
     */
    private String preview(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String text = new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
