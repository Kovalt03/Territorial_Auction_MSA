package com.territorial.realtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-key-for-realtime-ws-connect-verification-32b+";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(new JwtProperties(SECRET));
    }

    private String buildToken(String type, String subject, Date expiration, SecretKey signingKey) {
        return Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    @DisplayName("유효한 access 토큰 — 검증 통과 및 subject를 userId로 추출")
    @Test
    void validAccessToken_passesAndExtractsUserId() {
        String token =
                buildToken("access", "42", new Date(System.currentTimeMillis() + 60_000), KEY);

        assertThat(provider.validateAccessToken(token)).isTrue();
        assertThat(provider.getAccessTokenUserId(token)).isEqualTo(42L);
    }

    @DisplayName("access 타입이 아닌 토큰(refresh) — 검증 실패")
    @Test
    void nonAccessType_fails() {
        String token =
                buildToken("refresh", "42", new Date(System.currentTimeMillis() + 60_000), KEY);

        assertThat(provider.validateAccessToken(token)).isFalse();
    }

    @DisplayName("다른 secret으로 서명된 토큰 — 서명 불일치로 검증 실패")
    @Test
    void wrongSignature_fails() {
        SecretKey otherKey =
                Keys.hmacShaKeyFor(
                        "a-completely-different-secret-key-value-256bits!!"
                                .getBytes(StandardCharsets.UTF_8));
        String token =
                buildToken("access", "42", new Date(System.currentTimeMillis() + 60_000), otherKey);

        assertThat(provider.validateAccessToken(token)).isFalse();
    }

    @DisplayName("만료된 access 토큰 — 검증 실패")
    @Test
    void expiredToken_fails() {
        String token =
                buildToken("access", "42", new Date(System.currentTimeMillis() - 1_000), KEY);

        assertThat(provider.validateAccessToken(token)).isFalse();
    }

    @DisplayName("형식이 깨진 토큰 — 예외를 삼키고 검증 실패")
    @Test
    void malformedToken_fails() {
        assertThat(provider.validateAccessToken("not-a-jwt")).isFalse();
    }
}
