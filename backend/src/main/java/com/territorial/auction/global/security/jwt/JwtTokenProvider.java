package com.territorial.auction.global.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String DEFAULT_ROLE = "USER";

    public String createAccessToken(Long userId) {
        return createAccessToken(userId, DEFAULT_ROLE);
    }

    public String createAccessToken(Long userId, String role) {
        return buildToken(userId, role, ACCESS_TOKEN_TYPE, jwtProperties.accessTokenExpiry());
    }

    public String createRefreshToken(Long userId) {
        return buildToken(
                userId, DEFAULT_ROLE, REFRESH_TOKEN_TYPE, jwtProperties.refreshTokenExpiry());
    }

    private String buildToken(Long userId, String role, String tokenType, long expiryMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(getSigningKey())
                .compact();
    }

    public Long getAccessTokenUserId(String token) {
        return getUserId(token, ACCESS_TOKEN_TYPE);
    }

    public Long getRefreshTokenUserId(String token) {
        return getUserId(token, REFRESH_TOKEN_TYPE);
    }

    public boolean validateAccessToken(String token) {
        try {
            return ACCESS_TOKEN_TYPE.equals(getClaims(token).get(TOKEN_TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Long getUserId(String token, String expectedType) {
        Claims claims = getClaims(token);
        if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("잘못된 JWT token type");
        }
        return Long.parseLong(claims.getSubject());
    }

    // 과거 발급 토큰(role claim 없음) 하위호환을 위해 기본값 USER 반환
    public String getRole(String token) {
        Object role = getClaims(token).get(ROLE_CLAIM);
        return role != null ? role.toString() : DEFAULT_ROLE;
    }

    public long getRemainingMs(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            return Math.max(0, expiration.getTime() - System.currentTimeMillis());
        } catch (JwtException e) {
            return 0;
        }
    }

    public boolean validate(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT 만료: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT 검증 실패: {}", e.getMessage());
        }
        return false;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
