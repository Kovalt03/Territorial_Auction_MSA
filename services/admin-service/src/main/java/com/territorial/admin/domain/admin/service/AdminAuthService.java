package com.territorial.admin.domain.admin.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 자체 인증. 게임 유저(user-service)와 분리된 admin_accounts·전용 토큰·IP 허용목록·2FA로 동작한다. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtProvider adminJwtProvider;
    private final AdminRefreshTokenService adminRefreshTokenService;
    private final TotpService totpService;
    private final AdminSecurityProperties adminSecurityProperties;

    @Transactional
    public AdminLoginResult login(AdminLoginRequest request, String clientIp) {
        validateIpAllowed(clientIp);
        AdminAccount account = findByEmailOrThrow(request.email());
        validateActive(account);
        validatePassword(request.password(), account.getPasswordHash());
        validateTotp(account, request.totpCode());

        String accessToken =
                adminJwtProvider.createAccessToken(account.getId(), account.getRole().name());
        String refreshToken = adminJwtProvider.createRefreshToken(account.getId());
        adminRefreshTokenService.save(account.getId(), refreshToken);

        log.info(
                "관리자 로그인 성공. adminId={}, totpEnrolled={}",
                account.getId(),
                account.isTotpEnrolled());
        return new AdminLoginResult(accessToken, refreshToken, account.isTotpEnrolled());
    }

    @Transactional
    public TotpSetupResponse setupTotp(Long adminId) {
        AdminAccount account =
                adminAccountRepository
                        .findById(adminId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND));
        String secret = totpService.generateSecret();
        account.enrollTotp(secret);
        log.info("관리자 TOTP 등록. adminId={}", adminId);
        return new TotpSetupResponse(
                secret, totpService.buildOtpAuthUri(secret, account.getEmail()));
    }

    private void validateIpAllowed(String clientIp) {
        if (!adminSecurityProperties.isIpAllowed(clientIp)) {
            log.warn("관리자 로그인 차단 - 허용되지 않은 IP. ip={}", clientIp);
            throw new CustomException(ErrorCode.ADMIN_IP_NOT_ALLOWED);
        }
    }

    private AdminAccount findByEmailOrThrow(String email) {
        return adminAccountRepository
                .findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
    }

    private void validateActive(AdminAccount account) {
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.ADMIN_ACCOUNT_SUSPENDED);
        }
    }

    private void validatePassword(String raw, String hash) {
        if (!passwordEncoder.matches(raw, hash)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    // TOTP 미등록이면 최초 로그인 허용(등록 유도), 등록됐으면 코드 검증
    private void validateTotp(AdminAccount account, String totpCode) {
        if (account.isTotpEnrolled() && !totpService.verify(account.getTotpSecret(), totpCode)) {
            throw new CustomException(ErrorCode.INVALID_TOTP_CODE);
        }
    }
}
