package com.aiinterview.ai.voice;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * OpenAI 兼容协议的语音转写实现（{@code app.voice.provider=openai}）。
 *
 * <p>适配 OpenAI Whisper 以及所有兼容 {@code POST /audio/transcriptions} 的服务
 * （SiliconFlow、自建 faster-whisper 等），只需改 {@code app.voice.base-url} 与
 * {@code app.voice.model}。</p>
 *
 * <p>这里手写 multipart 而不是用 {@code MultipartBodyBuilder}：后者基于流式输出，
 * 请求会带 {@code Transfer-Encoding: chunked}，部分兼容服务（例如 SiliconFlow）
 * 会直接返回 500。手写后请求体是 byte[]，{@link HttpClient} 会自动带上
 * {@code Content-Length}，与 curl 的行为一致。</p>
 */
@Component
@ConditionalOnProperty(name = "app.voice.provider", havingValue = "openai")
public class OpenAiAsrClient implements AsrClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAsrClient.class);

    /** multipart 边界前缀，实际边界会追加随机串 */
    private static final String BOUNDARY_PREFIX = "----AiInterviewFormBoundary";

    private final VoiceProperties properties;
    private final HttpClient httpClient;

    /**
     * 按配置构建带连接超时的 HTTP 客户端。
     *
     * @param properties 语音转写配置
     */
    public OpenAiAsrClient(VoiceProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    /**
     * 调用 {@code /audio/transcriptions} 完成转写。
     *
     * <p>未配置 api-key 时直接给出可操作的提示；真实调用中的任何异常（网络、鉴权、
     * 响应解析）都包装成 {@link ErrorCode#AI_ERROR}，避免上层拿到难以理解的底层异常。</p>
     *
     * @param audioFile 音频文件绝对路径
     * @param language  识别语言，为空时使用配置的默认语言
     * @return 转写结果
     */
    @Override
    public AsrResult transcribe(Path audioFile, String language) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BizException(ErrorCode.AI_ERROR, "语音转写未配置：请设置 APP_ASR_API_KEY（或 app.voice.api-key）");
        }
        if (audioFile == null || !Files.isReadable(audioFile)) {
            throw new BizException(ErrorCode.AI_ERROR, "语音文件不存在或不可读");
        }
        String effectiveLanguage = StringUtils.hasText(language) ? language : properties.getLanguage();
        long start = System.currentTimeMillis();
        try {
            byte[] audio = Files.readAllBytes(audioFile);
            String boundary = BOUNDARY_PREFIX + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipartBody(boundary, audioFile, audio, effectiveLanguage);
            String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new BizException(ErrorCode.AI_ERROR,
                        "语音转写失败：HTTP " + response.statusCode() + " " + truncate(response.body()));
            }
            JsonNode root = JsonUtils.mapper().readTree(response.body());
            JsonNode textNode = root.path("text");
            String text = textNode.isMissingNode() || textNode.isNull() ? "" : textNode.asText();
            String model = root.path("model").asText(properties.getModel());
            long latency = System.currentTimeMillis() - start;
            log.debug("语音转写完成 model={} latency={}ms length={}", model, latency, text.length());
            return new AsrResult(text, model, providerName(), latency);
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AI_ERROR, "语音转写被中断", e);
        } catch (Exception e) {
            log.warn("语音转写失败 baseUrl={} model={} error={}",
                    properties.getBaseUrl(), properties.getModel(), e.getMessage());
            throw new BizException(ErrorCode.AI_ERROR, "语音转写失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构造 multipart/form-data 请求体。
     *
     * @param boundary  边界字符串
     * @param audioFile 音频文件（用于文件名与扩展名推断 Content-Type）
     * @param audio     音频字节
     * @param language  识别语言，可为空
     * @return 完整请求体
     * @throws IOException 写入失败
     */
    private byte[] buildMultipartBody(String boundary, Path audioFile, byte[] audio, String language)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(audio.length + 1024);
        String filename = audioFile.getFileName() == null ? "audio" : audioFile.getFileName().toString();
        String contentType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        writeFilePart(out, boundary, "file", filename, contentType, audio);
        writeField(out, boundary, "model", properties.getModel());
        if (StringUtils.hasText(language)) {
            writeField(out, boundary, "language", language);
        }
        writeField(out, boundary, "response_format", "json");
        write(out, ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * 写入文件 part。
     *
     * @param out         输出流
     * @param boundary    边界
     * @param name        字段名
     * @param filename    文件名
     * @param contentType 文件 MIME
     * @param content     文件字节
     * @throws IOException 写入失败
     */
    private void writeFilePart(ByteArrayOutputStream out, String boundary, String name, String filename,
                               String contentType, byte[] content) throws IOException {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        write(out, header.getBytes(StandardCharsets.UTF_8));
        write(out, content);
        write(out, "\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 写入普通文本 part。
     *
     * @param out      输出流
     * @param boundary 边界
     * @param name     字段名
     * @param value    字段值
     * @throws IOException 写入失败
     */
    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        write(out, part.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 写入字节并吞掉 {@link ByteArrayOutputStream} 不会抛出的 IO 异常。
     *
     * @param out   输出流
     * @param bytes 字节
     * @throws IOException 写入失败
     */
    private void write(ByteArrayOutputStream out, byte[] bytes) throws IOException {
        out.write(bytes);
    }

    /**
     * 截断错误响应，避免把超长 HTML 直接返回给前端。
     *
     * @param body 响应体
     * @return 截断后的文本
     */
    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    /**
     * 返回提供方标识。
     *
     * @return 固定返回 {@code openai}，与 {@code app.voice.provider} 取值一致
     */
    @Override
    public String providerName() {
        return "openai";
    }
}
