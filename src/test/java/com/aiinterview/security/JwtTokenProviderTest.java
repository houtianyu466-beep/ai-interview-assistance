package com.aiinterview.security;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与校验单元测试。
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-with-enough-length-123456");
        properties.setExpireMinutes(60);
        properties.setIssuer("ai-interview-platform");
        provider = new JwtTokenProvider(properties);
    }

    @Test
    @DisplayName("签发的令牌可以被正确解析")
    void shouldCreateAndParseToken() {
        String token = provider.createToken(new LoginUser(100L, "candidate", "CANDIDATE"));

        TokenPayload payload = provider.parseToken(token);

        assertThat(payload.userId()).isEqualTo(100L);
        assertThat(payload.username()).isEqualTo("candidate");
        assertThat(payload.role()).isEqualTo("CANDIDATE");
        assertThat(payload.jti()).isNotBlank();
        assertThat(payload.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    @DisplayName("被篡改的令牌签名校验失败")
    void shouldRejectTamperedToken() {
        String token = provider.createToken(new LoginUser(1L, "admin", "ADMIN"));
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"role\":\"ADMIN\"}".getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> provider.parseToken(forged))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("格式非法的令牌被拒绝")
    void shouldRejectMalformedToken() {
        assertThatThrownBy(() -> provider.parseToken("not-a-jwt"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("格式");
    }

    @Test
    @DisplayName("过期令牌被拒绝")
    void shouldRejectExpiredToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-with-enough-length-123456");
        properties.setExpireMinutes(-1);
        JwtTokenProvider expiredProvider = new JwtTokenProvider(properties);
        String token = expiredProvider.createToken(new LoginUser(2L, "candidate", "CANDIDATE"));

        assertThatThrownBy(() -> expiredProvider.parseToken(token))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过期");
    }

    @Test
    @DisplayName("密钥过短时启动失败")
    void shouldRejectShortSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");
        assertThatThrownBy(() -> new JwtTokenProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
