package com.aiinterview.ai.voice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
     * 直接把内存中的音频字节转写成文字（流式场景使用）。
     *
     * <p>默认实现落一个临时文件后复用 {@link #transcribe(Path, String)}，
     * 这样已实现的客户端无需改动即可支持流式分片转写；临时文件用完即删。</p>
     *
     * @param audio     音频字节（WAV）
     * @param fileName  临时文件名，扩展名决定 MIME，建议 {@code segment.wav}
     * @param language  识别语言
     * @return 转写结果
     */
    default AsrResult transcribe(byte[] audio, String fileName, String language) {
        Path temp = null;
        try {
            temp = Files.createTempFile("asr-segment-", ".wav");
            Files.write(temp, audio, StandardOpenOption.TRUNCATE_EXISTING);
            return transcribe(temp, language);
        } catch (IOException e) {
            throw new IllegalStateException("流式转写临时文件写入失败", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响转写结果
                }
            }
        }
    }

    /**
     * 返回当前实现对应的提供方名称，写入响应便于前端排查。
     *
     * @return 提供方标识，如 openai、mock
     */
    String providerName();
}
