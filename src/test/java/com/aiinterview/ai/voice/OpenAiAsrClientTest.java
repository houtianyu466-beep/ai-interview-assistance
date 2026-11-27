package com.aiinterview.ai.voice;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 兼容语音转写客户端测试。
 *
 * <p>重点锁定两个真实踩过的坑：</p>
 * <ul>
 *     <li>请求必须是带 {@code Content-Length} 的普通 multipart，不能是 chunked ——
 *         SiliconFlow 对 chunked 上传直接返回 500；</li>
 *     <li>错误状态码要转成可读的 {@link BizException}，不能把底层异常抛给上层。</li>
 * </ul>
 */
class OpenAiAsrClientTest {

    private HttpServer server;

    /**
     * 关闭测试 HTTP 服务，避免端口泄漏。
     */
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /**
     * 校验 multipart 结构：文件字段、模型字段、Content-Length，并解析响应文本。
     *
     * @throws IOException 创建临时文件或启动服务失败
     */
    @Test
    @DisplayName("转写请求为带 Content-Length 的 multipart，且能解析 text 字段")
    void shouldSendBufferedMultipart() throws IOException {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> contentLength = new AtomicReference<>();
        AtomicReference<String> transferEncoding = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();

        startServer(exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"text\":\"你好，我是张伟\",\"model\":\"FunAudioLLM/SenseVoiceSmall\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Path wav = Files.createTempFile("asr-test", ".wav");
        Files.write(wav, "RIFF-test-audio".getBytes(StandardCharsets.UTF_8));

        AsrResult result = client().transcribe(wav, "zh");
        Files.deleteIfExists(wav);

        assertThat(result.text()).isEqualTo("你好，我是张伟");
        assertThat(result.model()).isEqualTo("FunAudioLLM/SenseVoiceSmall");
        assertThat(result.provider()).isEqualTo("openai");
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);

        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(contentType.get()).startsWith("multipart/form-data; boundary=");
        assertThat(contentLength.get()).isNotNull();
        assertThat(transferEncoding.get()).isNull();
        assertThat(body.get())
                .contains("name=\"file\"")
                .contains("name=\"model\"")
                .contains("FunAudioLLM/SenseVoiceSmall")
                .contains("name=\"language\"")
                .contains("zh")
                .contains("RIFF-test-audio");
    }

    /**
     * 未配置 Key 时给出可操作的提示，且不发起请求。
     */
    @Test
    @DisplayName("未配置 API Key 时提示配置方式")
    void shouldRejectWhenApiKeyMissing() {
        VoiceProperties properties = new VoiceProperties();
        properties.setApiKey("");
        OpenAiAsrClient client = new OpenAiAsrClient(properties);

        assertThatThrownBy(() -> client.transcribe(Path.of("not-exists.wav"), "zh"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("APP_ASR_API_KEY");
    }

    /**
     * 服务返回 4xx 时包装成 BizException 并带上状态码。
     *
     * @throws IOException 创建临时文件或启动服务失败
     */
    @Test
    @DisplayName("服务返回 401 时抛出带状态码的业务异常")
    void shouldWrapHttpError() throws IOException {
        startServer(exchange -> {
            byte[] response = "{\"error\":\"invalid api key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Path wav = Files.createTempFile("asr-error", ".wav");
        Files.write(wav, "RIFF".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client().transcribe(wav, "zh"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("401")
                .extracting(error -> ((BizException) error).getErrorCode())
                .isEqualTo(ErrorCode.AI_ERROR);

        Files.deleteIfExists(wav);
    }

    /**
     * 构造指向测试服务的客户端。
     *
     * @return 转写客户端
     */
    private OpenAiAsrClient client() {
        VoiceProperties properties = new VoiceProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setModel("FunAudioLLM/SenseVoiceSmall");
        properties.setTimeoutSeconds(10);
        return new OpenAiAsrClient(properties);
    }

    /**
     * 启动一次性测试 HTTP 服务。
     *
     * @param handler 请求处理逻辑
     * @throws IOException 端口绑定失败
     */
    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio/transcriptions", handler);
        server.start();
    }
}
