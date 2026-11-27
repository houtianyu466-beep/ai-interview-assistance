package com.aiinterview.ai.voice;

import java.nio.file.Path;

/**
 * 语音转写（ASR）客户端抽象。
 *
 * <p>面向接口编程：业务层只依赖本接口，具体是真实转写服务（{@link OpenAiAsrClient}）
 * 还是未配置占位实现（{@link UnavailableAsrClient}）由 {@code app.voice.provider} 决定。</p>
 */
public interface AsrClient {

    /**
     * 把音频文件转写为文字。
     *
     * @param audioFile 已落盘的音频文件绝对路径
     * @param language  识别语言（如 zh、en）；为空时由实现回退到配置的默认值
     * @return 转写结果
     */
    AsrResult transcribe(Path audioFile, String language);

    /**
     * 返回当前实现对应的提供方名称，写入响应便于前端排查。
     *
     * @return 提供方标识，如 openai、mock
     */
    String providerName();
}
