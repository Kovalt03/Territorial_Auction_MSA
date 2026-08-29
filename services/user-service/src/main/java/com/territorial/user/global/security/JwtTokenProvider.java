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
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(Long userId, String role) {
        return createToken(userId, role, properties.accessTokenExpiry());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, "USER", properties.refreshTokenExpiry());
    }

    public Long getUserId(String token) {
        return Long.parseLong(claims(token).getSubject());
    }

    private String createToken(Long userId, String role, long expiryMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(signingKey())
                .compact();
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
