package com.territorial.user.global.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "session:jwt_refresh:";
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public void save(Long userId, String refreshToken) {
        redisTemplate
                .opsForValue()
                .set(
                        KEY_PREFIX + userId,
                        refreshToken,
                        Duration.ofMillis(jwtProperties.refreshTokenExpiry()));
    }

    public boolean isValid(Long userId, String refreshToken) {
        return refreshToken.equals(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
    }

    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
