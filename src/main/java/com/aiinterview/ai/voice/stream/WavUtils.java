package com.aiinterview.ai.voice.stream;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * PCM 与 WAV 互转工具。
 *
 * <p>浏览器端把麦克风音频降采样成 16kHz / 16bit / 单声道 PCM 后按帧发送，
 * 服务端 VAD 切出一段话音后需要把它包装成带 44 字节头的 WAV，
 * 才能直接交给现有 {@code /audio/transcriptions} 转写客户端。</p>
 */
public final class WavUtils {

    /** WAV 头长度 */
    private static final int HEADER_SIZE = 44;
    /** PCM 16bit 采样字节数 */
    private static final int BYTES_PER_SAMPLE = 2;

    private WavUtils() {
    }

    /**
     * 把 PCM16 字节包装成 WAV。
     *
     * @param pcm        16bit 小端 PCM 数据
     * @param sampleRate 采样率，例如 16000
     * @param channels   声道数，单声道传 1
     * @return 完整 WAV 字节
     */
    public static byte[] pcm16ToWav(byte[] pcm, int sampleRate, int channels) {
        int dataSize = pcm == null ? 0 : pcm.length;
        int byteRate = sampleRate * channels * BYTES_PER_SAMPLE;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(36 + dataSize);
        buffer.put(new byte[]{'W', 'A', 'V', 'E'});
        buffer.put(new byte[]{'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) (channels * BYTES_PER_SAMPLE));
        buffer.putShort((short) 16);
        buffer.put(new byte[]{'d', 'a', 't', 'a'});
        buffer.putInt(dataSize);
        if (dataSize > 0) {
            buffer.put(pcm);
        }
        return buffer.array();
    }

    /**
     * 计算一段 PCM16 的均方根音量，归一化到 0~1。
     *
     * @param pcm 16bit 小端 PCM
     * @return 归一化 RMS；空数据返回 0
     */
    public static double rms(byte[] pcm) {
        if (pcm == null || pcm.length < BYTES_PER_SAMPLE) {
            return 0.0;
        }
        long sum = 0;
        int samples = pcm.length / BYTES_PER_SAMPLE;
        for (int i = 0; i < samples; i++) {
            int low = pcm[i * 2] & 0xFF;
            int high = pcm[i * 2 + 1];
            int sample = (high << 8) | low;
            sum += (long) sample * sample;
        }
        double mean = (double) sum / samples;
        return Math.sqrt(mean) / Short.MAX_VALUE;
    }

    /**
     * 拼接两段 PCM。
     *
     * @param first  第一段，可为空
     * @param second 第二段，可为空
     * @return 拼接结果
     */
    public static byte[] concat(byte[] first, byte[] second) {
        if (first == null || first.length == 0) {
            return second == null ? new byte[0] : second;
        }
        if (second == null || second.length == 0) {
            return first;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(first.length + second.length);
        out.writeBytes(first);
        out.writeBytes(second);
        return out.toByteArray();
    }
}
