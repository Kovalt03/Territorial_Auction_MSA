package com.territorial.user.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(Long userId, String role) {
        return createToken(userId, role, ACCESS_TOKEN_TYPE, properties.accessTokenExpiry());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, "USER", REFRESH_TOKEN_TYPE, properties.refreshTokenExpiry());
    }

    public Long getAccessTokenUserId(String token) {
        return getUserId(token, ACCESS_TOKEN_TYPE);
    }

    public Long getRefreshTokenUserId(String token) {
        return getUserId(token, REFRESH_TOKEN_TYPE);
    }

    private String createToken(Long userId, String role, String tokenType, long expiryMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(signingKey())
                .compact();
    }

    private Long getUserId(String token, String expectedType) {
        Claims claims = claims(token);
        if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("잘못된 JWT token type");
        }
        return Long.parseLong(claims.getSubject());
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
