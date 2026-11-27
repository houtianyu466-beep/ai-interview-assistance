package com.aiinterview.ai.voice;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 未配置真实转写服务时的占位实现。
 *
 * <p>默认启用（{@code app.voice.provider=mock}）。此时语音链路的失败必须是「可照做的提示」，
 * 而不是让练习者停在加载中，因此转写直接抛出带引导文案的业务异常。</p>
 */
@Component
@ConditionalOnProperty(name = "app.voice.provider", havingValue = "mock", matchIfMissing = true)
public class UnavailableAsrClient implements AsrClient {

    /**
     * 直接提示未配置，不做任何网络调用。
     *
     * @param audioFile 音频文件路径（本实现不使用）
     * @param language  识别语言（本实现不使用）
     * @return 不会返回，总是抛出业务异常
     */
    @Override
    public AsrResult transcribe(Path audioFile, String language) {
        throw new BizException(ErrorCode.AI_ERROR,
                "语音转写未配置：请在 application.yml 将 app.voice.provider 设为 openai 并配置 api-key，或先用文字作答");
    }

    /**
     * 返回提供方标识。
     *
     * @return 固定返回 {@code mock}
     */
    @Override
    public String providerName() {
        return "mock";
    }
}
