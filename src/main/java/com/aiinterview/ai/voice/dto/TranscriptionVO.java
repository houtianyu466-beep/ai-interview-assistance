package com.aiinterview.ai.voice.dto;

/**
 * 语音转写响应体。
 *
 * <p>说明：音频时长在服务端无法可靠获取，因此只用 {@code latencyMs} 表示本次转写耗时，
 * 由前端按需展示，不做猜测。</p>
 *
 * @param text      识别出的文字
 * @param provider  转写服务提供方（如 openai、mock）
 * @param model     实际使用的模型名
 * @param latencyMs 本次转写耗时（毫秒）
 * @param audioPath 音频文件相对路径，可用于回放或后续排查
 */
public record TranscriptionVO(String text, String provider, String model, Long latencyMs, String audioPath) {
}
