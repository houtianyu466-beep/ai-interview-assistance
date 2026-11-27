package com.aiinterview.ai;

/**
 * 大模型返回结果。
 *
 * @param content          文本内容（期望是 JSON 字符串）
 * @param model            实际使用的模型
 * @param promptTokens     输入 token 数（部分服务不返回，可能为 null）
 * @param completionTokens 输出 token 数
 * @param latencyMs        调用耗时
 */
public record LlmResponse(String content, String model, Integer promptTokens, Integer completionTokens, long latencyMs) {
}
