package com.aiinterview.ai.voice.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 基于能量的流式 VAD 分片器（无第三方依赖）。
 *
 * <p>工作方式：每来一帧 PCM 就计算归一化 RMS，与「自适应阈值」比较：</p>
 * <ul>
 *     <li>阈值 = max(静态阈值, 噪声底 × 系数)，噪声底在非语音帧上做指数平滑，
 *         因此空调声、键盘声这类稳定底噪不会持续误触发；</li>
 *     <li>连续超过阈值达到 {@code minSpeechMs} 才认为进入语音，过滤咳嗽、点击等瞬时噪声；</li>
 *     <li>进入语音后，静音持续 {@code endSilenceMs} 即判定一句话结束，输出该段；</li>
 *     <li>单段超过 {@code maxSegmentMs} 强制切分，避免长句一直不出字；</li>
 *     <li>切分时向前保留 {@code preRollMs} 的音频，避免吞掉第一个字。</li>
 * </ul>
 *
 * <p>线程约定：实例非线程安全，一个 WebSocket 连接对应一个实例，只在该连接的
 * 处理线程中调用。</p>
 */
public class VadSegmenter {

    private static final Logger log = LoggerFactory.getLogger(VadSegmenter.class);

    private final int sampleRate;
    private final int frameMs;
    private final double energyThreshold;
    private final double noiseFactor;
    private final int minSpeechFrames;
    private final int endSilenceFrames;
    private final int maxSegmentFrames;
    private final int preRollFrames;

    /** 预卷环形缓冲：保存最近若干帧，用于切段时回补开头 */
    private final LinkedList<byte[]> preRoll = new LinkedList<>();
    /** 当前语音段的帧 */
    private final List<byte[]> current = new ArrayList<>();
    /** 连续超阈值的帧数 */
    private int speechRun;
    /** 语音段内的连续静音帧数 */
    private int silenceRun;
    /** 是否处于语音段 */
    private boolean inSpeech;
    /** 噪声底（指数平滑） */
    private double noiseFloor = 0.0035;

    /** 已输出段的序号 */
    private int segmentSeq;
    /** 当前帧对应的绝对起始毫秒 */
    private long frameCursorMs;

    /**
     * 构造分片器。
     *
     * @param properties 流式配置
     */
    public VadSegmenter(VoiceStreamProperties properties) {
        this.sampleRate = Math.max(8000, properties.getSampleRate());
        this.frameMs = Math.max(20, properties.getFrameMs());
        this.energyThreshold = Math.max(0.001, properties.getEnergyThreshold());
        this.noiseFactor = Math.max(1.5, properties.getNoiseFactor());
        this.minSpeechFrames = Math.max(1, properties.getMinSpeechMs() / frameMs);
        this.endSilenceFrames = Math.max(1, properties.getEndSilenceMs() / frameMs);
        this.maxSegmentFrames = Math.max(minSpeechFrames + 1, properties.getMaxSegmentMs() / frameMs);
        this.preRollFrames = Math.max(0, properties.getPreRollMs() / frameMs);
    }

    /**
     * 是否处于语音段内（供前端切换「正在说话」提示）。
     *
     * @return 处于语音段返回 {@code true}
     */
    public boolean isInSpeech() {
        return inSpeech;
    }

    /**
     * 送入一帧音频并返回本次切出的语音段。
     *
     * @param frame 16bit 小端 PCM，单帧时长约 {@code frameMs}
     * @return 本次切出的段列表；没有切段时为空列表
     */
    public List<Segment> accept(byte[] frame) {
        if (frame == null || frame.length == 0) {
            return List.of();
        }
        long frameStart = frameCursorMs;
        frameCursorMs += frameMs;
        double level = WavUtils.rms(frame);
        double threshold = Math.max(energyThreshold, noiseFloor * noiseFactor);

        if (!inSpeech) {
            pushPreRoll(frame);
            if (level > threshold) {
                speechRun++;
                if (speechRun >= minSpeechFrames) {
                    enterSpeech();
                }
            } else {
                speechRun = 0;
                // 只在非语音期更新噪声底，避免把说话声算进底噪
                noiseFloor = noiseFloor * 0.95 + level * 0.05;
            }
            return List.of();
        }

        current.add(frame);
        if (level > threshold) {
            silenceRun = 0;
        } else {
            silenceRun++;
        }

        if (silenceRun >= endSilenceFrames || current.size() >= maxSegmentFrames) {
            boolean forced = silenceRun < endSilenceFrames;
            SegmentSegmentResult result = finishSegment(frameStart, forced);
            return result.segments();
        }
        return List.of();
    }

    /**
     * 结束录音：若仍有未输出的语音段则强制输出。
     *
     * @return 最后一段；没有内容时为空列表
     */
    public List<Segment> flush() {
        if (!inSpeech || current.isEmpty()) {
            reset();
            return List.of();
        }
        return finishSegment(frameCursorMs, true).segments();
    }

    /**
     * 进入语音段：把预卷帧补到段首，保证第一个字不被截断。
     */
    private void enterSpeech() {
        inSpeech = true;
        silenceRun = 0;
        speechRun = 0;
        current.clear();
        current.addAll(preRoll);
        preRoll.clear();
        log.debug("VAD 检测到语音开始，预卷帧数={}", current.size());
    }

    /**
     * 结束当前段并重置状态。
     *
     * @param frameStart 触发切段的帧起始毫秒
     * @param forced     是否为强制切段（长段或显式结束）
     * @return 本次输出
     */
    private SegmentSegmentResult finishSegment(long frameStart, boolean forced) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] chunk : current) {
            out.writeBytes(chunk);
        }
        byte[] pcm = out.toByteArray();
        int durationMs = current.size() * frameMs;
        long endMs = frameStart + frameMs;
        long startMs = Math.max(0L, endMs - durationMs);
        Segment segment = new Segment(++segmentSeq, pcm, startMs, endMs, durationMs, forced);
        reset();
        log.debug("VAD 切出语音段 seq={} 时长={}ms 强制={}", segment.seq(), durationMs, forced);
        return new SegmentSegmentResult(List.of(segment));
    }

    /** 清空语音段状态，但保留噪声底与序号。 */
    private void reset() {
        inSpeech = false;
        silenceRun = 0;
        speechRun = 0;
        current.clear();
        preRoll.clear();
    }

    /**
     * 维护预卷环形缓冲。
     *
     * @param frame 当前帧
     */
    private void pushPreRoll(byte[] frame) {
        if (preRollFrames <= 0) {
            return;
        }
        preRoll.addLast(frame);
        while (preRoll.size() > preRollFrames) {
            preRoll.removeFirst();
        }
    }

    /**
     * 切段结果包装（避免 List.of() 与自定义类型混淆）。
     *
     * @param segments 段列表
     */
    private record SegmentSegmentResult(List<Segment> segments) {
    }

    /**
     * 一段语音。
     *
     * @param seq        段序号，从 1 开始
     * @param pcm        16bit 小端 PCM
     * @param startMs    相对录音开始的毫秒
     * @param endMs      相对录音开始的毫秒
     * @param durationMs 时长
     * @param forced     是否因超长或结束而强制切分
     */
    public record Segment(int seq, byte[] pcm, long startMs, long endMs, int durationMs, boolean forced) {
    }
}
