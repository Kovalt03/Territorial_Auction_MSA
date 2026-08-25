package com.territorial.auction.global.security.jwt;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public void save(Long userId, String refreshToken) {
        redisTemplate
                .opsForValue()
                .set(
                        "session:jwt_refresh:" + userId,
                        refreshToken,
                        Duration.ofMillis(jwtProperties.refreshTokenExpiry()));
    }

    public String get(Long userId) {
        return redisTemplate.opsForValue().get("session:jwt_refresh:" + userId);
    }

    public void delete(Long userId) {
        redisTemplate.delete("session:jwt_refresh:" + userId);
    }

    public boolean isValid(Long userId, String refreshToken) {
        String stored = get(userId);
        return refreshToken.equals(stored);
    }
}
