package com.territorial.user.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.auth.dto.LoginRequest;
import com.territorial.user.domain.auth.dto.TokenPair;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.event.UserCreatedEventPublisher;
import com.territorial.user.global.exception.ErrorCode;
import com.territorial.user.global.security.JwtTokenProvider;
import com.territorial.user.global.security.RefreshTokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private UserCreatedEventPublisher userCreatedEventPublisher;

    @Test
    void loginActiveUserIssuesAndStoresTokenPair() {
        User user = user(1L, "ACTIVE");
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1!", "hash")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("access");
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh");

        TokenPair result = authService.login(new LoginRequest("user@example.com", "password1!"));

        assertThat(result).isEqualTo(new TokenPair("access", "refresh"));
        verify(refreshTokenService).save(1L, "refresh");
    }

    @Test
    void loginSuspendedUserIsRejected() {
        given(userRepository.findByEmail("user@example.com"))
                .willReturn(Optional.of(user(1L, "SUSPENDED")));

        assertThatThrownBy(
                        () -> authService.login(new LoginRequest("user@example.com", "password1!")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SUSPENDED_USER);
    }

    @Test
    void malformedRefreshTokenIsNormalizedToUnauthorized() {
        given(jwtTokenProvider.getRefreshTokenUserId("bad-token"))
                .willThrow(new IllegalArgumentException("bad token"));

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void refreshWithdrawnUserIsRejected() {
        given(jwtTokenProvider.getRefreshTokenUserId("refresh")).willReturn(1L);
        given(refreshTokenService.isValid(1L, "refresh")).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "WITHDRAWN")));

        assertThatThrownBy(() -> authService.refresh("refresh"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    @Test
    void duplicateUsernameCheckUsesPublicContractError() {
        given(userRepository.existsByUsername("taken")).willReturn(true);

        assertThatThrownBy(() -> authService.checkUsername("taken"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_USERNAME);
    }

    private User user(long id, String status) {
        User user =
                User.builder()
                        .username("user")
                        .email("user@example.com")
                        .passwordHash("hash")
                        .nickname("nickname")
                        .build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
