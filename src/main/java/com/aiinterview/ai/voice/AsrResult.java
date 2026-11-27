package com.aiinterview.ai.voice;

/**
 * 语音转写结果（不可变）。
 *
 * <p>只包含转写服务真正返回的信息，不伪造音频时长等无法可靠获取的字段。</p>
 *
 * @param text      识别出的文字，可能为空字符串
 * @param model     实际使用的模型名
 * @param provider  转写服务提供方（如 openai、mock）
 * @param latencyMs 本次转写耗时（毫秒）
 */
public record AsrResult(String text, String model, String provider, long latencyMs) {
}
