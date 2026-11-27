package com.aiinterview.ai.voice;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 未配置语音合成时的占位实现（{@code app.tts.provider=none}，默认）。
 *
 * <p>直接抛 503 语义的业务异常，前端据此退回浏览器 {@code speechSynthesis}，
 * 阅读体验不受影响，只是音色不如真实 TTS 自然。</p>
 */
@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "none", matchIfMissing = true)
public class UnavailableTtsClient implements TtsClient {

    /**
     * 永远提示未配置。
     *
     * @param text  文本
     * @param voice 音色
     * @return 不会返回
     */
    @Override
    public TtsResult synthesize(String text, String voice) {
        throw new BizException(ErrorCode.AI_ERROR,
                "语音合成未配置：可设置 APP_TTS_PROVIDER=openai 或 volcengine");
    }

    /**
     * 恒为不可用。
     *
     * @return {@code false}
     */
    @Override
    public boolean enabled() {
        return false;
    }

    /**
     * 返回提供方标识。
     *
     * @return none
     */
    @Override
    public String providerName() {
        return "none";
    }
}
