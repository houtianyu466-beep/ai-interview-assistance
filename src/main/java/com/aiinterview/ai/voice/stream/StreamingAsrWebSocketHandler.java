package com.aiinterview.ai.voice.stream;

import com.aiinterview.ai.voice.AsrClient;
import com.aiinterview.ai.voice.AsrResult;
import com.aiinterview.common.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式语音转写 WebSocket 处理器。
 *
 * <p>协议（客户端 → 服务端）：</p>
 * <ul>
 *     <li>二进制帧：16bit 小端 PCM，单声道，采样率见 {@code READY} 消息；</li>
 *     <li>文本帧：{@code {"type":"STOP"}} 请求收尾，{@code {"type":"PING"}} 心跳。</li>
 * </ul>
 *
 * <p>协议（服务端 → 客户端）：</p>
 * <ul>
 *     <li>{@code READY}：{@code {sampleRate, frameMs}}，连接建立后立即下发；</li>
 *     <li>{@code SPEECH_START}：检测到说话开始，前端可据此切换「正在说话」状态；</li>
 *     <li>{@code TRANSCRIPT}：{@code {text, seq, startMs, endMs, latencyMs, provider}}，
 *         每切出一句就推送一条，前端按顺序拼接即可实时显示；</li>
 *     <li>{@code ERROR}：{@code {message}}，转写失败但连接保留，可继续说话；</li>
 *     <li>{@code STOPPED}：收尾完成。</li>
 * </ul>
 *
 * <p>并发模型：连接线程只做 VAD 与切段，转写交给共享线程池；线程池满时丢弃最旧的
 * 待转写段并回一条 {@code ERROR}，保证实时性优先于完整性。</p>
 */
@Component
public class StreamingAsrWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamingAsrWebSocketHandler.class);

    /** 会话属性键：分片器。 */
    public static final String ATTR_SEGMENTER = "vadSegmenter";
    /** 会话属性键：语言。 */
    public static final String ATTR_LANGUAGE = "language";
    /** 会话属性键：是否已开始说话。 */
    private static final String ATTR_SPEECH_STARTED = "speechStarted";

    private final AsrClient asrClient;
    private final VoiceStreamProperties properties;
    private final ExecutorService executor;

    /**
     * 构造器注入并按配置创建转写线程池。
     *
     * @param asrClient  转写客户端
     * @param properties 流式配置
     */
    public StreamingAsrWebSocketHandler(AsrClient asrClient, VoiceStreamProperties properties) {
        this.asrClient = asrClient;
        this.properties = properties;
        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(Math.max(1, properties.getWorkerThreads()), runnable -> {
            Thread thread = new Thread(runnable, "streaming-asr-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 连接建立：初始化分片器并下发 READY。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(ATTR_SEGMENTER, new VadSegmenter(properties));
        if (!session.getAttributes().containsKey(ATTR_LANGUAGE)) {
            session.getAttributes().put(ATTR_LANGUAGE, "zh");
        }
        Map<String, Object> ready = new LinkedHashMap<>();
        ready.put("type", "READY");
        ready.put("sampleRate", properties.getSampleRate());
        ready.put("frameMs", properties.getFrameMs());
        ready.put("endSilenceMs", properties.getEndSilenceMs());
        ready.put("provider", asrClient.providerName());
        send(session, ready);
        log.info("流式转写连接建立 sessionId={} sampleRate={} provider={}",
                session.getId(), properties.getSampleRate(), asrClient.providerName());
    }

    /**
     * 处理二进制音频帧：VAD 切段 → 异步转写。
     *
     * @param session WebSocket 会话
     * @param message 二进制音频帧
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        VadSegmenter segmenter = (VadSegmenter) session.getAttributes().get(ATTR_SEGMENTER);
        if (segmenter == null) {
            return;
        }
        byte[] frame = new byte[message.getPayloadLength()];
        message.getPayload().get(frame);
        List<VadSegmenter.Segment> segments;
        try {
            segments = segmenter.accept(frame);
        } catch (RuntimeException e) {
            log.warn("VAD 处理失败 sessionId={} reason={}", session.getId(), e.getMessage());
            sendError(session, "音频分片处理失败");
            return;
        }
        for (VadSegmenter.Segment segment : segments) {
            submit(session, segment);
        }
        boolean speechStarted = Boolean.TRUE.equals(session.getAttributes().get(ATTR_SPEECH_STARTED));
        // 通过「VAD 是否处于语音段」通知前端说话状态，用于 UI 上的呼吸动画
        if (!speechStarted && segmenter.isInSpeech()) {
            session.getAttributes().put(ATTR_SPEECH_STARTED, Boolean.TRUE);
            send(session, Map.of("type", "SPEECH_START"));
        } else if (speechStarted && !segmenter.isInSpeech()) {
            session.getAttributes().put(ATTR_SPEECH_STARTED, Boolean.FALSE);
        }
    }

    /**
     * 处理文本控制消息。
     *
     * @param session WebSocket 会话
     * @param message 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if (payload == null) {
            return;
        }
        String type;
        try {
            type = JsonUtils.mapper().readTree(payload).path("type").asText("");
        } catch (Exception e) {
            sendError(session, "消息格式不正确");
            return;
        }
        switch (type) {
            case "STOP" -> {
                VadSegmenter segmenter = (VadSegmenter) session.getAttributes().get(ATTR_SEGMENTER);
                if (segmenter != null) {
                    for (VadSegmenter.Segment segment : segmenter.flush()) {
                        submit(session, segment);
                    }
                }
                send(session, Map.of("type", "STOPPED"));
            }
            case "PING" -> send(session, Map.of("type", "PONG"));
            case "LANGUAGE" -> {
                try {
                    String language = JsonUtils.mapper().readTree(payload).path("language").asText("zh");
                    session.getAttributes().put(ATTR_LANGUAGE, language);
                } catch (Exception ignored) {
                    // 语言解析失败沿用默认值
                }
            }
            default -> sendError(session, "不支持的消息类型：" + type);
        }
    }

    /**
     * 连接关闭：清理线程池引用之外的状态。
     *
     * @param session WebSocket 会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        session.getAttributes().remove(ATTR_SEGMENTER);
        log.info("流式转写连接关闭 sessionId={} status={}", session.getId(), status);
    }

    /**
     * 把切出的语音段提交到转写线程池。
     *
     * @param session WebSocket 会话
     * @param segment 语音段
     */
    private void submit(WebSocketSession session, VadSegmenter.Segment segment) {
        if (segment == null || segment.pcm().length == 0) {
            return;
        }
        String language = String.valueOf(session.getAttributes().getOrDefault(ATTR_LANGUAGE, "zh"));
        byte[] wav = WavUtils.pcm16ToWav(segment.pcm(), properties.getSampleRate(), 1);
        try {
            executor.execute(() -> transcribe(session, segment, wav, language));
        } catch (RejectedExecutionException e) {
            sendError(session, "转写繁忙，已跳过一段，请继续说");
        }
    }

    /**
     * 执行一次分片转写并推送结果。
     *
     * @param session  WebSocket 会话
     * @param segment  语音段
     * @param wav      WAV 字节
     * @param language 语言
     */
    private void transcribe(WebSocketSession session, VadSegmenter.Segment segment, byte[] wav, String language) {
        long start = System.currentTimeMillis();
        try {
            AsrResult result = asrClient.transcribe(wav, "segment.wav", language);
            String text = result.text() == null ? "" : result.text().trim();
            long latency = System.currentTimeMillis() - start;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "TRANSCRIPT");
            payload.put("text", text);
            payload.put("seq", segment.seq());
            payload.put("startMs", segment.startMs());
            payload.put("endMs", segment.endMs());
            payload.put("durationMs", segment.durationMs());
            payload.put("latencyMs", latency);
            payload.put("provider", result.provider());
            send(session, payload);
            log.info("流式转写完成 sessionId={} seq={} 时长={}ms 耗时={}ms 字数={}",
                    session.getId(), segment.seq(), segment.durationMs(), latency, text.length());
        } catch (RuntimeException e) {
            log.warn("流式转写失败 sessionId={} seq={} reason={}",
                    session.getId(), segment.seq(), e.getMessage());
            sendError(session, "转写失败：" + e.getMessage());
        }
    }

    /**
     * 发送 JSON 消息，连接已关闭时忽略。
     *
     * @param session WebSocket 会话
     * @param payload 消息体
     */
    private void send(WebSocketSession session, Map<String, Object> payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = JsonUtils.mapper().writeValueAsString(payload);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("流式转写消息发送失败 sessionId={} reason={}", session.getId(), e.getMessage());
        }
    }

    /**
     * 发送错误消息。
     *
     * @param session WebSocket 会话
     * @param message 可读错误信息
     */
    private void sendError(WebSocketSession session, String message) {
        send(session, Map.of("type", "ERROR", "message", message));
    }
}
