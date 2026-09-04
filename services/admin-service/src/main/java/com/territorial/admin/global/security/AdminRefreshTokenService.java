package com.territorial.admin.global.security;

import com.territorial.admin.global.config.AdminJwtProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 관리자 리프레시 토큰 저장. 공유 Redis에서 유저 토큰(session:jwt_refresh:)과 키 네임스페이스를 분리한다. */
@Service
@RequiredArgsConstructor
public class AdminRefreshTokenService {

    private static final String KEY_PREFIX = "session:admin_refresh:";

    private final StringRedisTemplate redisTemplate;
    private final AdminJwtProperties properties;

    public void save(Long adminId, String refreshToken) {
        redisTemplate
                .opsForValue()
                .set(
                        KEY_PREFIX + adminId,
                        refreshToken,
                        Duration.ofMillis(properties.refreshTokenValidityMs()));
    }

    public String get(Long adminId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + adminId);
    }

    public void delete(Long adminId) {
        redisTemplate.delete(KEY_PREFIX + adminId);
    }

    public boolean isValid(Long adminId, String refreshToken) {
        return refreshToken.equals(get(adminId));
    }
}
