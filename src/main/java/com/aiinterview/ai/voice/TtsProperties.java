package com.aiinterview.ai.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音合成（TTS）配置，对应 {@code app.tts.*}。
 *
 * <p>provider 取值：</p>
 * <ul>
 *     <li>{@code none}：不启用，前端退回浏览器 speechSynthesis；</li>
 *     <li>{@code openai}：OpenAI 兼容的 {@code POST /audio/speech}（OpenAI、硅基流动、各类代理）；</li>
 *     <li>{@code volcengine}：火山引擎（豆包）HTTP 语音合成接口。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.tts")
public class TtsProperties {

    /** 提供方：none | openai | volcengine */
    private String provider = "none";
    /** OpenAI 兼容接口的 base-url（末尾不带斜杠） */
    private String baseUrl = "https://api.openai.com/v1";
    /** OpenAI 兼容接口的 Key */
    private String apiKey = "";
    /** 模型名，如 tts-1、FunAudioLLM/CosyVoice2-0.5B */
    private String model = "tts-1";
    /** 音色，如 alloy、FunAudioLLM/CosyVoice2-0.5B:anna */
    private String voice = "alloy";
    /** 输出格式 */
    private String responseFormat = "mp3";
    /** 语速倍率（1.0 为正常） */
    private double speed = 1.0;
    /** 火山引擎 appid */
    private String appId = "";
    /** 火山引擎 access token */
    private String accessToken = "";
    /** 火山引擎 cluster，如 volcano_tts */
    private String cluster = "volcano_tts";
    /** 火山引擎音色，如 zh_female_shuangkuaisisi_moon_bigtts */
    private String volcVoice = "zh_female_shuangkuaisisi_moon_bigtts";
    /** 单次合成文本上限 */
    private int maxChars = 1200;
    /** 连接与读取超时（秒） */
    private int timeoutSeconds = 30;

    /** 返回提供方。 */
    public String getProvider() {
        return provider;
    }

    /** 设置提供方。 */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /** 返回 OpenAI 兼容 base-url。 */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** 设置 OpenAI 兼容 base-url。 */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** 返回 Key。 */
    public String getApiKey() {
        return apiKey;
    }

    /** 设置 Key。 */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /** 返回模型名。 */
    public String getModel() {
        return model;
    }

    /** 设置模型名。 */
    public void setModel(String model) {
        this.model = model;
    }

    /** 返回音色。 */
    public String getVoice() {
        return voice;
    }

    /** 设置音色。 */
    public void setVoice(String voice) {
        this.voice = voice;
    }

    /** 返回输出格式。 */
    public String getResponseFormat() {
        return responseFormat;
    }

    /** 设置输出格式。 */
    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    /** 返回语速倍率。 */
    public double getSpeed() {
        return speed;
    }

    /** 设置语速倍率。 */
    public void setSpeed(double speed) {
        this.speed = speed;
    }

    /** 返回火山引擎 appid。 */
    public String getAppId() {
        return appId;
    }

    /** 设置火山引擎 appid。 */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** 返回火山引擎 access token。 */
    public String getAccessToken() {
        return accessToken;
    }

    /** 设置火山引擎 access token。 */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /** 返回火山引擎 cluster。 */
    public String getCluster() {
        return cluster;
    }

    /** 设置火山引擎 cluster。 */
    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    /** 返回火山引擎音色。 */
    public String getVolcVoice() {
        return volcVoice;
    }

    /** 设置火山引擎音色。 */
    public void setVolcVoice(String volcVoice) {
        this.volcVoice = volcVoice;
    }

    /** 返回单次合成文本上限。 */
    public int getMaxChars() {
        return maxChars;
    }

    /** 设置单次合成文本上限。 */
    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    /** 返回超时时间。 */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /** 设置超时时间。 */
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
