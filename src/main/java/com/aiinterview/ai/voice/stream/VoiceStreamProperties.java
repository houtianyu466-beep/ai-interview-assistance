package com.aiinterview.ai.voice.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 流式语音转写配置，对应 {@code app.voice.stream.*}。
 *
 * <p>时间片与 VAD 参数共同决定延迟：</p>
 * <ul>
 *     <li>{@code frame-ms}：客户端每帧音频的时长，越小上行越及时，默认 100ms；</li>
 *     <li>{@code end-silence-ms}：认定一句话结束所需的静音时长，默认 600ms，
 *         这是「说完到出字」的主要延迟来源；</li>
 *     <li>{@code min-speech-ms}：低于该时长的音量突变视为噪声，默认 200ms；</li>
 *     <li>{@code max-segment-ms}：单段最长时长，超过则强制切段，默认 15s，防止长句阻塞；</li>
 *     <li>{@code pre-roll-ms}：切段时向前保留的音频，避免吞掉首字，默认 200ms。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.voice.stream")
public class VoiceStreamProperties {

    /** 是否启用流式转写端点 */
    private boolean enabled = true;
    /** 采样率，必须与浏览器端一致 */
    private int sampleRate = 16000;
    /** 单帧时长（毫秒） */
    private int frameMs = 100;
    /** VAD 绝对能量下限（0~1，归一化 RMS）：再安静的环境也不会低于它 */
    private double energyThreshold = 0.006;
    /** 噪声底自适应系数：起始阈值 = max(绝对下限, 噪声底 × 系数) */
    private double noiseFactor = 2.2;
    /** 判定「仍然在说话」的阈值比例（迟滞）：句中停顿用更低的阈值，避免一句话被切碎 */
    private double endThresholdRatio = 0.55;
    /** 判定语音开始前的平滑窗口帧数，抑制单帧毛刺 */
    private int smoothFrames = 3;
    /** 认定语音开始的最短持续时长 */
    private int minSpeechMs = 160;
    /** 认定语音结束的静音时长 */
    private int endSilenceMs = 600;
    /** 单段最长时长 */
    private int maxSegmentMs = 15000;
    /** 切段时向前保留的音频 */
    private int preRollMs = 240;
    /** 转写并发线程数 */
    private int workerThreads = 4;
    /** 等待转写队列长度，超出时丢弃最旧的待转写段 */
    private int queueCapacity = 8;
    /** STOP 后等待在途转写的最长时间，超时也会回 STOPPED */
    private int stopTimeoutMs = 20000;

    /** 返回是否启用流式端点。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置是否启用流式端点。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回采样率。 */
    public int getSampleRate() {
        return sampleRate;
    }

    /** 设置采样率。 */
    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /** 返回单帧时长。 */
    public int getFrameMs() {
        return frameMs;
    }

    /** 设置单帧时长。 */
    public void setFrameMs(int frameMs) {
        this.frameMs = frameMs;
    }

    /** 返回静态能量阈值。 */
    public double getEnergyThreshold() {
        return energyThreshold;
    }

    /** 设置静态能量阈值。 */
    public void setEnergyThreshold(double energyThreshold) {
        this.energyThreshold = energyThreshold;
    }

    /** 返回噪声自适应系数。 */
    public double getNoiseFactor() {
        return noiseFactor;
    }

    /** 设置噪声自适应系数。 */
    public void setNoiseFactor(double noiseFactor) {
        this.noiseFactor = noiseFactor;
    }

    /** 返回最短语音时长。 */
    public int getMinSpeechMs() {
        return minSpeechMs;
    }

    /** 设置最短语音时长。 */
    public void setMinSpeechMs(int minSpeechMs) {
        this.minSpeechMs = minSpeechMs;
    }

    /** 返回判定句尾的静音时长。 */
    public int getEndSilenceMs() {
        return endSilenceMs;
    }

    /** 设置判定句尾的静音时长。 */
    public void setEndSilenceMs(int endSilenceMs) {
        this.endSilenceMs = endSilenceMs;
    }

    /** 返回单段最长时长。 */
    public int getMaxSegmentMs() {
        return maxSegmentMs;
    }

    /** 设置单段最长时长。 */
    public void setMaxSegmentMs(int maxSegmentMs) {
        this.maxSegmentMs = maxSegmentMs;
    }

    /** 返回预卷时长。 */
    public int getPreRollMs() {
        return preRollMs;
    }

    /** 设置预卷时长。 */
    public void setPreRollMs(int preRollMs) {
        this.preRollMs = preRollMs;
    }

    /** 返回转写线程数。 */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /** 设置转写线程数。 */
    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    /** 返回队列长度。 */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /** 设置队列长度。 */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
    /** 返回判定「仍在说话」的阈值比例。 */
    public double getEndThresholdRatio() {
        return endThresholdRatio;
    }

    /** 设置判定「仍在说话」的阈值比例。 */
    public void setEndThresholdRatio(double endThresholdRatio) {
        this.endThresholdRatio = endThresholdRatio;
    }

    /** 返回平滑窗口帧数。 */
    public int getSmoothFrames() {
        return smoothFrames;
    }

    /** 设置平滑窗口帧数。 */
    public void setSmoothFrames(int smoothFrames) {
        this.smoothFrames = smoothFrames;
    }

    /** 返回 STOP 后等待在途转写的超时时间。 */
    public int getStopTimeoutMs() {
        return stopTimeoutMs;
    }

    /** 设置 STOP 后等待在途转写的超时时间。 */
    public void setStopTimeoutMs(int stopTimeoutMs) {
        this.stopTimeoutMs = stopTimeoutMs;
    }
}