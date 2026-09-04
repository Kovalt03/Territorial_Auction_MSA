package com.territorial.admin.global.security;

import com.territorial.admin.global.config.AdminJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 관리자 전용 토큰 발급·검증. 공개 유저 JWT와 **분리된 서명키 + issuer(admin-service)**를 강제해, 유저용 토큰이 관리자 컨텍스트로 혼입되는 권한
 * 상승을 차단한다.
 */
@Slf4j
@Component
public class AdminJwtProvider {

    private static final String ISSUER = "admin-service";
    private static final String ROLE_CLAIM = "role";
    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey signingKey;
    private final long accessValidityMs;
    private final long refreshValidityMs;

    public AdminJwtProvider(AdminJwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessValidityMs = properties.accessTokenValidityMs();
        this.refreshValidityMs = properties.refreshTokenValidityMs();
    }

    public String createAccessToken(Long adminId, String role) {
        return build(adminId, role, ACCESS, accessValidityMs);
    }

    public String createRefreshToken(Long adminId) {
        return build(adminId, null, REFRESH, refreshValidityMs);
    }

    private String build(Long adminId, String role, String type, long validityMs) {
        Date now = new Date();
        var builder =
                Jwts.builder()
                        .issuer(ISSUER)
                        .subject(String.valueOf(adminId))
                        .claim(TYPE_CLAIM, type)
                        .issuedAt(now)
                        .expiration(new Date(now.getTime() + validityMs))
                        .signWith(signingKey);
        if (role != null) {
            builder.claim(ROLE_CLAIM, role);
        }
        return builder.compact();
    }

    /** access 토큰 검증 성공 시 관리자 id 반환, 실패 시 null. */
    public Long parseAccessTokenAdminId(String token) {
        try {
            Claims claims = parse(token);
            if (!ACCESS.equals(claims.get(TYPE_CLAIM, String.class))) {
                return null;
            }
            return Long.parseLong(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String getRole(String token) {
        try {
            String role = parse(token).get(ROLE_CLAIM, String.class);
            return role != null ? role : "ADMIN";
        } catch (JwtException | IllegalArgumentException e) {
            return "ADMIN";
        }
    }

    public Long parseRefreshTokenAdminId(String token) {
        Claims claims = parse(token);
        if (!REFRESH.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("잘못된 토큰 타입");
        }
        return Long.parseLong(claims.getSubject());
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER) // 유저 토큰(issuer 불일치) 거부
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
