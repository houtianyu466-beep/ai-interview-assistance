package com.aiinterview.ai;

import java.util.List;

/**
 * 大模型客户端抽象。
 *
 * <p>实现类：</p>
 * <ul>
 *     <li>{@link OpenAiCompatibleLlmClient}：真实模型调用（OpenAI 协议）；</li>
 *     <li>{@link MockLlmClient}：离线桩实现，保证项目在无 API Key 时也能跑通全流程。</li>
 * </ul>
 *
 * <p>由 {@code app.ai.provider} 决定注入哪一个。</p>
 */
public interface LlmClient {

    /**
     * 发起一次对话补全。
     *
     * @param scene       业务场景，仅用于日志与监控
     * @param messages    对话消息
     * @param temperature 采样温度
     * @return 模型响应
     */
    LlmResponse chat(String scene, List<LlmMessage> messages, double temperature);

    /** 提供方名称，例如 mock / openai */
    String providerName();

    /** 模型名称 */
    String modelName();
}
