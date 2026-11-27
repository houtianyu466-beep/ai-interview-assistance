package com.aiinterview.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项，对应 application.yml 中的 {@code app.jwt.*}。
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥，长度至少 32 个字符 */
    private String secret;
    /** 过期时间（分钟） */
    private long expireMinutes = 240;
    /** 签发者 */
    private String issuer = "ai-interview-platform";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(long expireMinutes) {
        this.expireMinutes = expireMinutes;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
