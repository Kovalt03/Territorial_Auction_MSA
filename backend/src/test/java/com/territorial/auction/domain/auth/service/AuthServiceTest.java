package com.territorial.auction.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.auth.dto.LoginRequest;
import com.territorial.auction.domain.auth.dto.TokenPair;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserStatus;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;

    @Nested
    @DisplayName("login()")
    class Login {

        private User activeUser() {
            User user =
                    User.builder()
                            .username("testuser")
                            .email("user@example.com")
                            .passwordHash("encoded")
                            .nickname("닉네임")
                            .build();
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        }

        private User userWithStatus(UserStatus status) {
            User user = activeUser();
            ReflectionTestUtils.setField(user, "status", status);
            return user;
        }

        @Test
        @DisplayName("정상 로그인 시 TokenPair 반환")
        void login_success() {
            LoginRequest request = new LoginRequest("user@example.com", "password1!");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(activeUser()));
            given(passwordEncoder.matches("password1!", "encoded")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh-token");

            TokenPair tokenPair = authService.login(request);

            assertThat(tokenPair.accessToken()).isEqualTo("access-token");
            assertThat(tokenPair.refreshToken()).isEqualTo("refresh-token");
            then(refreshTokenService).should().save(1L, "refresh-token");
        }

        @Test
        @DisplayName("존재하지 않는 email 시 INVALID_CREDENTIALS 예외")
        void login_userNotFound() {
            LoginRequest request = new LoginRequest("unknown@example.com", "password1!");
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("탈퇴 유저 로그인 시 WITHDRAWN_USER 예외")
        void login_withdrawnUser() {
            LoginRequest request = new LoginRequest("user@example.com", "password1!");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(userWithStatus(UserStatus.WITHDRAWN)));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.WITHDRAWN_USER);
        }

        @Test
        @DisplayName("정지 유저 로그인 시 SUSPENDED_USER 예외")
        void login_suspendedUser() {
            LoginRequest request = new LoginRequest("user@example.com", "password1!");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(userWithStatus(UserStatus.SUSPENDED)));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SUSPENDED_USER);
        }

        @Test
        @DisplayName("비밀번호 불일치 시 INVALID_CREDENTIALS 예외")
        void login_invalidPassword() {
            LoginRequest request = new LoginRequest("user@example.com", "wrongPassword1!");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(activeUser()));
            given(passwordEncoder.matches("wrongPassword1!", "encoded")).willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("logout 시 refreshToken 삭제")
        void logout_deletesRefreshToken() {
            authService.logout(1L);

            then(refreshTokenService).should().delete(1L);
        }
    }

    @Nested
    @DisplayName("checkNickname()")
    class CheckNickname {

        @Test
        @DisplayName("사용 가능한 닉네임이면 예외 없음")
        void checkNickname_available() {
            given(userRepository.existsByNickname("닉네임")).willReturn(false);

            authService.checkNickname("닉네임");
        }

        @Test
        @DisplayName("중복 닉네임이면 DUPLICATE_NICKNAME 예외")
        void checkNickname_duplicate() {
            given(userRepository.existsByNickname("닉네임")).willReturn(true);

            assertThatThrownBy(() -> authService.checkNickname("닉네임"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Nested
    @DisplayName("checkEmail()")
    class CheckEmail {

        @Test
        @DisplayName("사용 가능한 이메일이면 예외 없음")
        void checkEmail_available() {
            given(userRepository.existsByEmail("user@example.com")).willReturn(false);

            authService.checkEmail("user@example.com");
        }

        @Test
        @DisplayName("중복 이메일이면 DUPLICATE_EMAIL 예외")
        void checkEmail_duplicate() {
            given(userRepository.existsByEmail("user@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.checkEmail("user@example.com"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Nested
    @DisplayName("checkUsername()")
    class CheckUsername {

        @Test
        @DisplayName("사용 가능한 유저네임이면 예외 없음")
        void checkUsername_available() {
            given(userRepository.existsByUsername("testuser")).willReturn(false);

            authService.checkUsername("testuser");
        }

        @Test
        @DisplayName("중복 유저네임이면 DUPLICATE_USERNAME 예외")
        void checkUsername_duplicate() {
            given(userRepository.existsByUsername("testuser")).willReturn(true);

            assertThatThrownBy(() -> authService.checkUsername("testuser"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_USERNAME);
        }
    }
}
