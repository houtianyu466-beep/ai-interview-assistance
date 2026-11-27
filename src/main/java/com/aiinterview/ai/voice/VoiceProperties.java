package com.aiinterview.ai.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音录制转写配置，对应 {@code app.voice.*}。
 *
 * <p>provider 取值：</p>
 * <ul>
 *     <li>{@code mock}：默认值，不接真实服务，调用转写直接提示未配置；</li>
 *     <li>{@code openai}：调用 OpenAI 兼容的 {@code /audio/transcriptions} 接口，如 OpenAI Whisper、
 *         SiliconFlow、自建 faster-whisper 服务等。</li>
 * </ul>
 *
 * <p>注意：{@code maxSize} 只在此处做业务校验，Servlet 容器层面还有
 * {@code spring.servlet.multipart.max-file-size} 限制，两者需保持一致，否则请求会在进入 Controller 前被拒绝。</p>
 */
@ConfigurationProperties(prefix = "app.voice")
public class VoiceProperties {

    /** 转写服务提供方：mock | openai */
    private String provider = "mock";
    /** OpenAI 兼容接口的基础地址，末尾不要带斜杠（如 https://api.openai.com/v1） */
    private String baseUrl = "https://api.openai.com/v1";
    /** 接口密钥，留空表示未配置 */
    private String apiKey = "";
    /** 转写模型，如 whisper-1 */
    private String model = "whisper-1";
    /** 默认识别语言，接口未显式指定时使用 */
    private String language = "zh";
    /** 连接与读取超时（秒） */
    private int timeoutSeconds = 180;
    /** 单个语音文件允许的最大字节数 */
    private long maxSize = 25 * 1024 * 1024;

    /** 返回转写服务提供方。 */
    public String getProvider() {
        return provider;
    }

    /** 设置转写服务提供方。 */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /** 返回接口基础地址。 */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** 设置接口基础地址。 */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** 返回接口密钥。 */
    public String getApiKey() {
        return apiKey;
    }

    /** 设置接口密钥。 */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /** 返回转写模型名。 */
    public String getModel() {
        return model;
    }

    /** 设置转写模型名。 */
    public void setModel(String model) {
        this.model = model;
    }

    /** 返回默认识别语言。 */
    public String getLanguage() {
        return language;
    }

    /** 设置默认识别语言。 */
    public void setLanguage(String language) {
        this.language = language;
    }

    /** 返回超时时间（秒）。 */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /** 设置超时时间（秒）。 */
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 返回语音文件大小上限（字节）。 */
    public long getMaxSize() {
        return maxSize;
    }

    /** 设置语音文件大小上限（字节）。 */
    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }
}
