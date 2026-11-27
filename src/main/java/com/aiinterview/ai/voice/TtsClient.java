package com.aiinterview.ai.voice;

/**
 * 语音合成客户端抽象。
 *
 * <p>实现方按 {@code app.tts.provider} 选择：{@link OpenAiTtsClient}（OpenAI 兼容）、
 * {@link VolcengineTtsClient}（火山引擎/豆包）、{@link UnavailableTtsClient}（未配置）。
 * 未配置时接口返回 503，前端自动退回浏览器语音合成。</p>
 */
public interface TtsClient {

    /**
     * 合成语音。
     *
     * @param text  待合成文本，调用方负责长度限制
     * @param voice 音色，可为空表示使用配置默认值
     * @return 合成结果（音频字节 + MIME）
     */
    TtsResult synthesize(String text, String voice);

    /**
     * 是否已配置可用的合成服务。
     *
     * @return 可用返回 {@code true}
     */
    boolean enabled();

    /**
     * 返回提供方标识，供前端展示与排查。
     *
     * @return 如 openai、volcengine、none
     */
    String providerName();
}
