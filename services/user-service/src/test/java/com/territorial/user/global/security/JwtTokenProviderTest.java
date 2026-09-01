package com.territorial.user.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider(
                    new JwtProperties(
                            "test-secret-key-that-is-long-enough-for-hs256", 60_000, 120_000));

    @Test
    void accessAndRefreshTokensAreAcceptedOnlyForTheirOwnPurpose() {
        String accessToken = jwtTokenProvider.createAccessToken(7L, "USER");
        String refreshToken = jwtTokenProvider.createRefreshToken(7L);

        assertThat(jwtTokenProvider.getAccessTokenUserId(accessToken)).isEqualTo(7L);
        assertThat(jwtTokenProvider.getRefreshTokenUserId(refreshToken)).isEqualTo(7L);
        assertThatThrownBy(() -> jwtTokenProvider.getAccessTokenUserId(refreshToken))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtTokenProvider.getRefreshTokenUserId(accessToken))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
