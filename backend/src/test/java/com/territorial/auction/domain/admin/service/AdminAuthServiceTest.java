package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.admin.dto.AdminLoginRequest;
import com.territorial.auction.domain.admin.dto.AdminLoginResult;
import com.territorial.auction.domain.admin.dto.TotpSetupResponse;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserRole;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.AdminSecurityProperties;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import com.territorial.auction.global.security.totp.TotpService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
class AdminAuthServiceTest {

    @InjectMocks private AdminAuthService adminAuthService;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private TotpService totpService;
    @Mock private AdminSecurityProperties adminSecurityProperties;

    private User admin;

    @BeforeEach
    void setUp() {
        admin =
                User.builder()
                        .username("admin")
                        .email("admin@example.com")
                        .passwordHash("hashed")
                        .nickname("운영자")
                        .build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
    }

    @Nested
    @DisplayName("Login")
    class Login {

        private AdminLoginRequest request(String totpCode) {
            return new AdminLoginRequest("admin@example.com", "pw", totpCode);
        }

        @Test
        @DisplayName("TOTP 등록 관리자 + 유효 코드 → 토큰 발급")
        void success_enrolled() {
            ReflectionTestUtils.setField(admin, "totpSecret", "SECRET");
            given(adminSecurityProperties.isIpAllowed(anyString())).willReturn(true);
            given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("pw", "hashed")).willReturn(true);
            given(totpService.verify("SECRET", "123456")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(1L, "ADMIN")).willReturn("access");
            given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh");

            AdminLoginResult result = adminAuthService.login(request("123456"), "127.0.0.1");

            assertThat(result.accessToken()).isEqualTo("access");
            assertThat(result.totpEnrolled()).isTrue();
            then(refreshTokenService).should().save(1L, "refresh");
        }

        @Test
        @DisplayName("TOTP 미등록 관리자 → 최초 로그인 허용(코드 불필요), totpEnrolled=false")
        void success_notEnrolled() {
            given(adminSecurityProperties.isIpAllowed(anyString())).willReturn(true);
            given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("pw", "hashed")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(1L, "ADMIN")).willReturn("access");
            given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh");

            AdminLoginResult result = adminAuthService.login(request(null), "127.0.0.1");

            assertThat(result.totpEnrolled()).isFalse();
            then(totpService).should(never()).verify(anyString(), anyString());
        }

        @Test
        @DisplayName("허용되지 않은 IP → ADMIN_IP_NOT_ALLOWED")
        void ipNotAllowed() {
            given(adminSecurityProperties.isIpAllowed("1.2.3.4")).willReturn(false);

            assertThatThrownBy(() -> adminAuthService.login(request("123456"), "1.2.3.4"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ADMIN_IP_NOT_ALLOWED);
        }

        @Test
        @DisplayName("관리자 계정 아님 → NOT_ADMIN_ACCOUNT")
        void notAdmin() {
            ReflectionTestUtils.setField(admin, "role", UserRole.USER);
            given(adminSecurityProperties.isIpAllowed(anyString())).willReturn(true);
            given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> adminAuthService.login(request("123456"), "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_ADMIN_ACCOUNT);
        }

        @Test
        @DisplayName("TOTP 코드 불일치 → INVALID_TOTP_CODE")
        void invalidTotp() {
            ReflectionTestUtils.setField(admin, "totpSecret", "SECRET");
            given(adminSecurityProperties.isIpAllowed(anyString())).willReturn(true);
            given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("pw", "hashed")).willReturn(true);
            given(totpService.verify("SECRET", "000000")).willReturn(false);

            assertThatThrownBy(() -> adminAuthService.login(request("000000"), "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_TOTP_CODE);
        }
    }

    @Nested
    @DisplayName("SetupTotp")
    class SetupTotp {

        @Test
        @DisplayName("관리자 → 시크릿 생성·등록 + otpauth URI 반환")
        void success() {
            given(userRepository.findById(1L)).willReturn(Optional.of(admin));
            given(totpService.generateSecret()).willReturn("NEWSECRET");
            given(totpService.buildOtpAuthUri(eq("NEWSECRET"), anyString()))
                    .willReturn("otpauth://totp/x");

            TotpSetupResponse response = adminAuthService.setupTotp(1L);

            assertThat(response.secret()).isEqualTo("NEWSECRET");
            assertThat(response.otpAuthUri()).isEqualTo("otpauth://totp/x");
            assertThat(admin.getTotpSecret()).isEqualTo("NEWSECRET");
        }

        @Test
        @DisplayName("관리자 아님 → NOT_ADMIN_ACCOUNT")
        void notAdmin() {
            ReflectionTestUtils.setField(admin, "role", UserRole.USER);
            given(userRepository.findById(1L)).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> adminAuthService.setupTotp(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_ADMIN_ACCOUNT);
        }
    }
}
