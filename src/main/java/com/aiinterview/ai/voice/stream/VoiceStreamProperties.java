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
    /** VAD 静态能量阈值（0~1，归一化 RMS） */
    private double energyThreshold = 0.012;
    /** 噪声底自适应系数：阈值 = max(静态阈值, 噪声底 * factor) */
    private double noiseFactor = 3.0;
    /** 认定语音开始的最短持续时长 */
    private int minSpeechMs = 200;
    /** 认定语音结束的静音时长 */
    private int endSilenceMs = 600;
    /** 单段最长时长 */
    private int maxSegmentMs = 15000;
    /** 切段时向前保留的音频 */
    private int preRollMs = 200;
    /** 转写并发线程数 */
    private int workerThreads = 3;
    /** 等待转写队列长度，超出时丢弃最旧的待转写段 */
    private int queueCapacity = 4;

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
}
