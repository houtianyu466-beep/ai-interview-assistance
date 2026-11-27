package com.aiinterview.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 大模型配置，对应 {@code app.ai.*}。
 *
 * <p>provider 取值：{@code mock}（默认，内置规则/模板，无需外网）与 {@code openai}（任意 OpenAI 兼容接口，
 * 如 DeepSeek、通义千问、vLLM 自建服务）。</p>
 */
@ConfigurationProperties(prefix = "app.ai")
public class LlmProperties {

    private String provider = "mock";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private double temperature = 0.3;
    private int timeoutSeconds = 60;
    /**
     * 是否允许降级：AI 不可用时由业务层使用规则引擎兜底。
     * 该开关只做提示，真正兜底在各业务 Service 中完成。
     */
    private boolean fallbackEnabled = true;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}
