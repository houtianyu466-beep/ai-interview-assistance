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
        properties.setEnergyThreshold(0.006);
        properties.setNoiseFactor(2.2);
        properties.setMinSpeechMs(160);
        properties.setEndSilenceMs(600);
        properties.setMaxSegmentMs(15000);
        properties.setPreRollMs(240);
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
    /**
     * 缺陷回归：麦克风偏小声（RMS 约 0.014）时也必须能触发语音检测。
     *
     * <p>修复前静态阈值 0.012 对「降噪后只剩一半音量」的输入经常漏检，
     * 表现为用户说话但界面一直没有反应。</p>
     */
    @Test
    @DisplayName("小声输入也能触发语音检测")
    void shouldDetectQuietSpeech() {
        VadSegmenter segmenter = segmenter();
        List<VadSegmenter.Segment> segments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            segments.addAll(segmenter.accept(frame(0)));
        }
        // 振幅 0.02 ≈ RMS 0.014，属于偏小声但正常说话的输入
        for (int i = 0; i < 12; i++) {
            segments.addAll(segmenter.accept(frame(0.02)));
        }
        assertThat(segmenter.isInSpeech()).isTrue();
        for (int i = 0; i < 8; i++) {
            segments.addAll(segmenter.accept(frame(0)));
        }
        assertThat(segments).hasSize(1);
    }

    /**
     * 缺陷回归：句中短促停顿（200ms）不应把一句话切成两段。
     *
     * <p>修复前没有迟滞判定，正常换气或弱读音节就会被当成句尾，
     * 导致转写结果被切碎（例如只识别出「端开发经验。」）。</p>
     */
    @Test
    @DisplayName("句中短停顿不会切碎句子")
    void shouldNotSplitOnShortPause() {
        VadSegmenter segmenter = segmenter();
        List<VadSegmenter.Segment> segments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            segments.addAll(segmenter.accept(frame(0)));
        }
        for (int i = 0; i < 6; i++) {
            segments.addAll(segmenter.accept(frame(0.3)));
        }
        // 200ms 低能量停顿，低于 endSilenceMs=600
        for (int i = 0; i < 2; i++) {
            segments.addAll(segmenter.accept(frame(0.002)));
        }
        for (int i = 0; i < 6; i++) {
            segments.addAll(segmenter.accept(frame(0.3)));
        }
        assertThat(segments).isEmpty();
        assertThat(segmenter.isInSpeech()).isTrue();
        for (int i = 0; i < 8; i++) {
            segments.addAll(segmenter.accept(frame(0)));
        }
        assertThat(segments).hasSize(1);
    }

    /**
     * 缺陷回归：说话期间噪声底不能被抬高，否则阈值会随说话一起上涨导致后半句漏检。
     */
    @Test
    @DisplayName("说话期间噪声底不上涨")
    void shouldNotInflateNoiseFloorWhileSpeaking() {
        VadSegmenter segmenter = segmenter();
        for (int i = 0; i < 5; i++) {
            segmenter.accept(frame(0));
        }
        double before = segmenter.noiseFloor();
        for (int i = 0; i < 20; i++) {
            segmenter.accept(frame(0.3));
        }
        assertThat(segmenter.noiseFloor()).isLessThan(before * 1.2 + 0.001);
    }
}
