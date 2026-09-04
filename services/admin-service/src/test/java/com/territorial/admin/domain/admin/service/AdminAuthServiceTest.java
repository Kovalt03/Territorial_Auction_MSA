package com.territorial.admin.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.admin.domain.admin.dto.AdminLoginRequest;
import com.territorial.admin.domain.admin.dto.AdminLoginResult;
import com.territorial.admin.domain.admin.dto.TotpSetupResponse;
import com.territorial.admin.domain.admin.entity.AdminAccount;
import com.territorial.admin.domain.admin.repository.AdminAccountRepository;
import com.territorial.admin.global.config.AdminSecurityProperties;
import com.territorial.admin.global.exception.ErrorCode;
import com.territorial.admin.global.security.AdminJwtProvider;
import com.territorial.admin.global.security.AdminRefreshTokenService;
import com.territorial.admin.global.security.totp.TotpService;
import com.territorial.auction.global.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock private AdminAccountRepository adminAccountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AdminJwtProvider adminJwtProvider;
    @Mock private AdminRefreshTokenService refreshTokenService;
    @Mock private TotpService totpService;

    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        // AdminSecurityProperties는 record라 실제 인스턴스로 주입(빈 목록 → 전체 허용).
        adminAuthService = newService(new AdminSecurityProperties(List.of()));
    }

    private AdminAuthService newService(AdminSecurityProperties securityProperties) {
        return new AdminAuthService(
                adminAccountRepository,
                passwordEncoder,
                adminJwtProvider,
                refreshTokenService,
                totpService,
                securityProperties);
    }

    private AdminAccount admin(long id, String totpSecret, AdminAccount.Status status) {
        AdminAccount account =
                AdminAccount.builder()
                        .email("root@ta.local")
                        .passwordHash("hash")
                        .totpSecret(totpSecret)
                        .role(AdminAccount.Role.SUPER_ADMIN)
                        .build();
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "status", status);
        return account;
    }

    private AdminLoginRequest loginRequest(String totpCode) {
        return new AdminLoginRequest("root@ta.local", "pw", totpCode);
    }

    @Test
    void loginWithoutTotpEnrolledIssuesAndStoresTokens() {
        AdminAccount account = admin(1L, null, AdminAccount.Status.ACTIVE);
        given(adminAccountRepository.findByEmail("root@ta.local")).willReturn(Optional.of(account));
        given(passwordEncoder.matches("pw", "hash")).willReturn(true);
        given(adminJwtProvider.createAccessToken(1L, "SUPER_ADMIN")).willReturn("access");
        given(adminJwtProvider.createRefreshToken(1L)).willReturn("refresh");

        AdminLoginResult result = adminAuthService.login(loginRequest(null), "127.0.0.1");

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.totpEnrolled()).isFalse();
        verify(refreshTokenService).save(1L, "refresh");
    }

    @Test
    void loginWithEnrolledTotpVerifiesCode() {
        AdminAccount account = admin(1L, "SECRET", AdminAccount.Status.ACTIVE);
        given(adminAccountRepository.findByEmail("root@ta.local")).willReturn(Optional.of(account));
        given(passwordEncoder.matches("pw", "hash")).willReturn(true);
        given(totpService.verify("SECRET", "123456")).willReturn(true);
        given(adminJwtProvider.createAccessToken(1L, "SUPER_ADMIN")).willReturn("access");
        given(adminJwtProvider.createRefreshToken(1L)).willReturn("refresh");

        AdminLoginResult result = adminAuthService.login(loginRequest("123456"), "127.0.0.1");

        assertThat(result.totpEnrolled()).isTrue();
        verify(refreshTokenService).save(1L, "refresh");
    }

    @Test
    void loginRejectsWrongPassword() {
        AdminAccount account = admin(1L, null, AdminAccount.Status.ACTIVE);
        given(adminAccountRepository.findByEmail("root@ta.local")).willReturn(Optional.of(account));
        given(passwordEncoder.matches("pw", "hash")).willReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(loginRequest(null), "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenService, never()).save(any(), any());
    }

    @Test
    void loginRejectsUnknownEmail() {
        given(adminAccountRepository.findByEmail("root@ta.local")).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuthService.login(loginRequest(null), "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsSuspendedAccount() {
        AdminAccount account = admin(1L, null, AdminAccount.Status.SUSPENDED);
        given(adminAccountRepository.findByEmail("root@ta.local")).willReturn(Optional.of(account));

        assertThatThrownBy(() -> adminAuthService.login(loginRequest(null), "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADMIN_ACCOUNT_SUSPENDED);
    }

    @Test
    void loginRejectsInvalidTotpCode() {
        AdminAccount account = admin(1L, "SECRET", AdminAccount.Status.ACTIVE);
        given(adminAccountRepository.findByEmail("root@ta.local")).willReturn(Optional.of(account));
        given(passwordEncoder.matches("pw", "hash")).willReturn(true);
        given(totpService.verify("SECRET", "000000")).willReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(loginRequest("000000"), "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOTP_CODE);
    }

    @Test
    void loginRejectsDisallowedIp() {
        AdminAuthService restricted = newService(new AdminSecurityProperties(List.of("10.0.0.1")));

        assertThatThrownBy(() -> restricted.login(loginRequest(null), "203.0.113.9"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADMIN_IP_NOT_ALLOWED);
        // IP 차단은 계정 조회 전에 막는다.
        verify(adminAccountRepository, never()).findByEmail(any());
    }

    @Test
    void setupTotpEnrollsSecret() {
        AdminAccount account = admin(1L, null, AdminAccount.Status.ACTIVE);
        given(adminAccountRepository.findById(1L)).willReturn(Optional.of(account));
        given(totpService.generateSecret()).willReturn("NEWSECRET");
        given(totpService.buildOtpAuthUri("NEWSECRET", "root@ta.local")).willReturn("otpauth://x");

        TotpSetupResponse response = adminAuthService.setupTotp(1L);

        assertThat(response.secret()).isEqualTo("NEWSECRET");
        assertThat(response.otpAuthUri()).isEqualTo("otpauth://x");
        assertThat(account.isTotpEnrolled()).isTrue();
    }

    @Test
    void setupTotpRejectsMissingAccount() {
        given(adminAccountRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuthService.setupTotp(9L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND);
    }
}
