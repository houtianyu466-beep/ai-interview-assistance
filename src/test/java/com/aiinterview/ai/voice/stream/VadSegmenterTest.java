package com.aiinterview.ai.voice.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式 VAD 分片器测试：用合成 PCM 覆盖「全静音、一句话、长句强制切分」三类场景。
 *
 * <p>参数统一为 16kHz / 100ms 帧，便于按帧数推算时长：
 * 100ms 静音 = 1600 采样 = 3200 字节。</p>
 */
class VadSegmenterTest {

    /** 采样率 */
    private static final int SAMPLE_RATE = 16000;
    /** 帧时长 */
    private static final int FRAME_MS = 100;
    /** 单帧采样数 */
    private static final int SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_MS / 1000;

    /**
     * 全静音不应该切出任何段。
     */
    @Test
    @DisplayName("全静音时切不出语音段")
    void shouldNotSegmentSilence() {
        VadSegmenter segmenter = segmenter();
        List<VadSegmenter.Segment> all = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            all.addAll(segmenter.accept(frame(0)));
        }
        assertThat(all).isEmpty();
        assertThat(segmenter.isInSpeech()).isFalse();
    }

    /**
     * 一句话（说话 1 秒 + 静音 800ms）应切出恰好一段。
     */
    @Test
    @DisplayName("说话后静音达到阈值时切出一段")
    void shouldSegmentOneUtterance() {
        VadSegmenter segmenter = segmenter();
        List<VadSegmenter.Segment> segments = new ArrayList<>();
        // 先给 5 帧静音，让噪声底稳定
        for (int i = 0; i < 5; i++) {
            segments.addAll(segmenter.accept(frame(0)));
        }
        // 说话 10 帧（1 秒）
        for (int i = 0; i < 10; i++) {
            segments.addAll(segmenter.accept(frame(0.25)));
        }
        assertThat(segmenter.isInSpeech()).isTrue();
        assertThat(segments).isEmpty();
        // 静音 8 帧（800ms），超过 endSilenceMs=600
        for (int i = 0; i < 8; i++) {
            segments.addAll(segmenter.accept(frame(0)));
        }
        assertThat(segments).hasSize(1);
        VadSegmenter.Segment segment = segments.get(0);
        assertThat(segment.seq()).isEqualTo(1);
        assertThat(segment.durationMs()).isGreaterThanOrEqualTo(1000);
        assertThat(segment.pcm().length).isEqualTo(segment.durationMs() / FRAME_MS * SAMPLES_PER_FRAME * 2);
        assertThat(segment.forced()).isFalse();
        assertThat(segmenter.isInSpeech()).isFalse();
    }

    /**
     * 连续说话超过 maxSegmentMs 时强制切段，且不丢音频。
     */
    @Test
    @DisplayName("长句超过上限时强制切段")
    void shouldForceSplitLongSpeech() {
        VoiceStreamProperties properties = segmenterProperties();
        properties.setMaxSegmentMs(1000);
        VadSegmenter segmenter = new VadSegmenter(properties);
        List<VadSegmenter.Segment> segments = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            segments.addAll(segmenter.accept(frame(0.3)));
        }
        assertThat(segments.size()).isGreaterThanOrEqualTo(2);
        assertThat(segments.get(0).forced()).isTrue();
        assertThat(segments.get(0).durationMs()).isEqualTo(1000);
    }

    /**
     * flush 应把仍在累积的语音段输出，避免结尾吞字。
     */
    @Test
    @DisplayName("结束时 flush 输出未完成的一段")
    void shouldFlushPendingSpeech() {
        VadSegmenter segmenter = segmenter();
        for (int i = 0; i < 6; i++) {
            segmenter.accept(frame(0));
        }
        for (int i = 0; i < 8; i++) {
            segmenter.accept(frame(0.3));
        }
        List<VadSegmenter.Segment> tail = segmenter.flush();
        assertThat(tail).hasSize(1);
        assertThat(tail.get(0).pcm()).isNotEmpty();
        assertThat(segmenter.isInSpeech()).isFalse();
    }

    /**
     * WAV 包装应生成合法的 44 字节头与正确长度。
     */
    @Test
    @DisplayName("PCM 包装成 WAV 头部与长度正确")
    void shouldWrapWavHeader() {
        byte[] pcm = new byte[SAMPLES_PER_FRAME * 2];
        byte[] wav = WavUtils.pcm16ToWav(pcm, SAMPLE_RATE, 1);
        assertThat(wav).hasSize(44 + pcm.length);
        assertThat(new String(wav, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(wav, 8, 4)).isEqualTo("WAVE");
        assertThat(WavUtils.rms(pcm)).isZero();
    }

    /**
     * 构造默认参数的测试配置。
     *
     * @return 配置
     */
    private VoiceStreamProperties segmenterProperties() {
        VoiceStreamProperties properties = new VoiceStreamProperties();
        properties.setSampleRate(SAMPLE_RATE);
        properties.setFrameMs(FRAME_MS);
        properties.setEnergyThreshold(0.012);
        properties.setNoiseFactor(3.0);
        properties.setMinSpeechMs(200);
        properties.setEndSilenceMs(600);
        properties.setMaxSegmentMs(15000);
        properties.setPreRollMs(200);
        return properties;
    }

    /**
     * 构造分片器。
     *
     * @return 分片器
     */
    private VadSegmenter segmenter() {
        return new VadSegmenter(segmenterProperties());
    }

    /**
     * 生成一帧正弦波 PCM。
     *
     * @param amplitude 振幅（0~1）
     * @return 3200 字节 PCM
     */
    private byte[] frame(double amplitude) {
        byte[] pcm = new byte[SAMPLES_PER_FRAME * 2];
        for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
            double value = Math.sin(2 * Math.PI * 440 * i / SAMPLE_RATE) * amplitude;
            int sample = (int) Math.round(value * Short.MAX_VALUE);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm;
    }
}
