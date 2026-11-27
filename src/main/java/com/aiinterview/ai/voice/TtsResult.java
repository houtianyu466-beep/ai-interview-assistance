package com.aiinterview.ai.voice;

/**
 * 语音合成结果。
 *
 * @param audio      音频字节
 * @param contentType MIME，如 audio/mpeg
 * @param provider   提供方
 * @param latencyMs  合成耗时
 */
public record TtsResult(byte[] audio, String contentType, String provider, long latencyMs) {
}
