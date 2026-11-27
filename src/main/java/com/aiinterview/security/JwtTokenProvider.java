package com.aiinterview.security;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256），基于 JDK 自带的 HmacSHA256 实现，不依赖第三方库。
 *
 * <p>令牌结构：{@code base64Url(header).base64Url(payload).base64Url(signature)}。</p>
 * <p>校验顺序：格式 → 签名（常量时间比较）→ 签发者 → 过期时间。</p>
 */
@Component
public class JwtTokenProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final JwtProperties properties;
    private final byte[] secretBytes;

    public JwtTokenProvider(JwtProperties properties) {
        if (properties.getSecret() == null || properties.getSecret().length() < 32) {
            throw new IllegalStateException("app.jwt.secret 长度必须不少于 32 个字符");
        }
        this.properties = properties;
        this.secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 签发访问令牌。
     */
    public String createToken(LoginUser loginUser) {
        long nowSeconds = Instant.now().getEpochSecond();
        long expireSeconds = nowSeconds + properties.getExpireMinutes() * 60;

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(loginUser.id()));
        payload.put("username", loginUser.username());
        payload.put("role", loginUser.role());
        payload.put("iss", properties.getIssuer());
        payload.put("iat", nowSeconds);
        payload.put("exp", expireSeconds);
        payload.put("jti", UUID.randomUUID().toString().replace("-", ""));

        String headerPart = base64Url(JsonUtils.toJson(header).getBytes(StandardCharsets.UTF_8));
        String payloadPart = base64Url(JsonUtils.toJson(payload).getBytes(StandardCharsets.UTF_8));
        String signature = sign(headerPart + "." + payloadPart);
        return headerPart + "." + payloadPart + "." + signature;
    }

    /**
     * 校验并解析令牌，失败抛出 401 业务异常。
     */
    public TokenPayload parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少访问令牌");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌格式不正确");
        }
        String expected = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌签名校验失败");
        }
        JsonNode payload = JsonUtils.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        String issuer = payload.path("iss").asText();
        if (!properties.getIssuer().equals(issuer)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌签发者不匹配");
        }
        long exp = payload.path("exp").asLong();
        if (exp < Instant.now().getEpochSecond()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        return new TokenPayload(
                Long.valueOf(payload.path("sub").asText()),
                payload.path("username").asText(),
                payload.path("role").asText(),
                payload.path("jti").asText(),
                Instant.ofEpochSecond(exp));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA256));
            return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "令牌签名失败", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
