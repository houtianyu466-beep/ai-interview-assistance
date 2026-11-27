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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *     <li>{@code READY}：{@code {sampleRate, frameMs, endSilenceMs, provider}}；</li>
 *     <li>{@code SPEECH_START} / {@code SPEECH_END}：进入/离开语音段；</li>
 *     <li>{@code TRANSCRIPT}：{@code {text, seq, startMs, endMs, latencyMs, provider, final}}；</li>
 *     <li>{@code ERROR}：转写失败但连接保留；</li>
 *     <li>{@code STOPPED}：<b>所有在途转写都已推送之后</b>才发送，客户端据此收尾。</li>
 * </ul>
 *
 * <p>为什么 STOPPED 要等待：早期实现里 STOP 只把最后一段交给线程池就立刻回
 * STOPPED，前端收到后马上关闭连接，最后一句的转写结果到达时连接已断开，
 * 表现为「说完了却没出字」。现在用 pending 计数 + 超时兜底保证不丢尾句。</p>
 *
 * <p>并发模型：连接线程只做 VAD 与切段；转写交给有界线程池，队列满时丢弃最旧的
 * 待转写任务并回 {@code ERROR}，保证实时性优先于完整性。</p>
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
    /** 会话属性键：在途转写任务数。 */
    private static final String ATTR_PENDING = "pendingTranscripts";
    /** 会话属性键：是否已停止接收音频。 */
    private static final String ATTR_STOPPING = "stopping";
    /** 会话属性键：STOPPED 是否已发送。 */
    private static final String ATTR_STOPPED_SENT = "stoppedSent";

    /** 会话属性键：该连接专用的单线程执行器，保证分段按序转写。 */
    private static final String ATTR_EXECUTOR = "asrExecutor";

    private final AsrClient asrClient;
    private final VoiceStreamProperties properties;

    /**
     * 构造器注入。
     *
     * @param asrClient  转写客户端
     * @param properties 流式配置
     */
    public StreamingAsrWebSocketHandler(AsrClient asrClient, VoiceStreamProperties properties) {
        this.asrClient = asrClient;
        this.properties = properties;
    }

    /**
     * 连接建立：初始化分片器并下发 READY。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(ATTR_SEGMENTER, new VadSegmenter(properties));
        session.getAttributes().putIfAbsent(ATTR_LANGUAGE, "zh");
        session.getAttributes().put(ATTR_PENDING, new AtomicInteger());
        session.getAttributes().put(ATTR_EXECUTOR, newExecutor(session.getId()));
        session.getAttributes().put(ATTR_STOPPING, new AtomicBoolean(false));
        session.getAttributes().put(ATTR_STOPPED_SENT, new AtomicBoolean(false));
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
        if (segmenter == null || isStopping(session)) {
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
        if (!speechStarted && segmenter.isInSpeech()) {
            session.getAttributes().put(ATTR_SPEECH_STARTED, Boolean.TRUE);
            send(session, Map.of("type", "SPEECH_START"));
        } else if (speechStarted && !segmenter.isInSpeech()) {
            session.getAttributes().put(ATTR_SPEECH_STARTED, Boolean.FALSE);
            send(session, Map.of("type", "SPEECH_END"));
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
            case "STOP" -> stop(session);
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
     * 连接关闭：清理状态。
     *
     * @param session WebSocket 会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        session.getAttributes().remove(ATTR_SEGMENTER);
        Object executor = session.getAttributes().remove(ATTR_EXECUTOR);
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
        log.info("流式转写连接关闭 sessionId={} status={}", session.getId(), status);
    }

    /**
     * 收尾：flush 最后一段，等所有在途转写完成后再回 STOPPED。
     *
     * @param session WebSocket 会话
     */
    private void stop(WebSocketSession session) {
        AtomicBoolean stopping = stoppingFlag(session);
        if (stopping != null && !stopping.compareAndSet(false, true)) {
            return;
        }
        VadSegmenter segmenter = (VadSegmenter) session.getAttributes().get(ATTR_SEGMENTER);
        if (segmenter != null) {
            for (VadSegmenter.Segment segment : segmenter.flush()) {
                submit(session, segment);
            }
        }
        // 没有任何在途转写时才立刻回 STOPPED
        if (pending(session).get() == 0) {
            sendStopped(session);
            return;
        }
        // 兜底超时：即使某个转写卡住，也要让客户端能结束
        Thread watchdog = new Thread(() -> {
            long deadline = System.currentTimeMillis() + properties.getStopTimeoutMs();
            while (System.currentTimeMillis() < deadline) {
                if (pending(session).get() == 0) {
                    sendStopped(session);
                    return;
                }
                try {
                    Thread.sleep(80L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            log.warn("流式转写收尾超时 sessionId={} pending={}", session.getId(), pending(session).get());
            sendStopped(session);
        }, "streaming-asr-stop-" + session.getId());
        watchdog.setDaemon(true);
        watchdog.start();
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
        AtomicInteger pending = pending(session);
        pending.incrementAndGet();
        ExecutorService executor = executor(session);
        try {
            executor.execute(() -> {
                try {
                    transcribe(session, segment, wav, language);
                } finally {
                    if (pending.decrementAndGet() == 0 && isStopping(session)) {
                        sendStopped(session);
                    }
                }
            });
        } catch (RuntimeException e) {
            pending.decrementAndGet();
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
            payload.put("final", isStopping(session));
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
     * 取会话专用执行器，缺失时补建。
     *
     * @param session WebSocket 会话
     * @return 单线程执行器
     */
    private ExecutorService executor(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_EXECUTOR);
        if (value instanceof ExecutorService service) {
            return service;
        }
        ExecutorService service = newExecutor(session.getId());
        session.getAttributes().put(ATTR_EXECUTOR, service);
        return service;
    }

    /**
     * 创建一个「单线程 + 有界队列 + 丢弃最旧」的执行器。
     *
     * <p>每个连接一个单线程执行器，保证同一场练习的语音段按 seq 顺序转写，
     * 否则连续两段几乎同时结束时会返回乱序文本（实测出现过
     * 「高并发交易系统。后端开发经验。」这种倒序）。</p>
     *
     * @param sessionId 会话 id，用于线程命名
     * @return 执行器
     */
    private ExecutorService newExecutor(String sessionId) {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.getQueueCapacity())),
                runnable -> {
                    Thread thread = new Thread(runnable, "streaming-asr-" + sessionId);
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    /**
     * 发送 STOPPED，保证只发一次。
     *
     * @param session WebSocket 会话
     */
    private void sendStopped(WebSocketSession session) {
        AtomicBoolean sent = (AtomicBoolean) session.getAttributes().get(ATTR_STOPPED_SENT);
        if (sent == null || !sent.compareAndSet(false, true)) {
            return;
        }
        send(session, Map.of("type", "STOPPED"));
    }

    /**
     * 取在途计数，缺失时补一个。
     *
     * @param session WebSocket 会话
     * @return 计数器
     */
    private AtomicInteger pending(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_PENDING);
        if (value instanceof AtomicInteger counter) {
            return counter;
        }
        AtomicInteger counter = new AtomicInteger();
        session.getAttributes().put(ATTR_PENDING, counter);
        return counter;
    }

    /**
     * 取停止标记。
     *
     * @param session WebSocket 会话
     * @return 标记，缺失时返回 {@code null}
     */
    private AtomicBoolean stoppingFlag(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_STOPPING);
        return value instanceof AtomicBoolean flag ? flag : null;
    }

    /**
     * 是否已进入停止状态。
     *
     * @param session WebSocket 会话
     * @return 已停止返回 {@code true}
     */
    private boolean isStopping(WebSocketSession session) {
        AtomicBoolean flag = stoppingFlag(session);
        return flag != null && flag.get();
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
