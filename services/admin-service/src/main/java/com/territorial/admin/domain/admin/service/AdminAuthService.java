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

/**
 * 관리자 자체 인증 — 신원은 admin_accounts가 소유(공개 유저 user-service와 분리). 자체 서명키/issuer 토큰과 TOTP·IP 허용목록으로 관리자를
 * 검증한다. 공유 유저 JWT 경로를 거치지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtProvider adminJwtProvider;
    private final AdminRefreshTokenService refreshTokenService;
    private final TotpService totpService;
    private final AdminSecurityProperties adminSecurityProperties;

    @Transactional
    public AdminLoginResult login(AdminLoginRequest request, String clientIp) {
        validateIpAllowed(clientIp);
        AdminAccount admin = findByEmailOrThrow(request.email());
        validateActive(admin);
        validatePassword(request.password(), admin.getPasswordHash());
        validateTotp(admin, request.totpCode());

        String accessToken =
                adminJwtProvider.createAccessToken(admin.getId(), admin.getRole().name());
        String refreshToken = adminJwtProvider.createRefreshToken(admin.getId());
        refreshTokenService.save(admin.getId(), refreshToken);

        log.info("관리자 로그인 성공. adminId={}, totpEnrolled={}", admin.getId(), admin.isTotpEnrolled());
        return new AdminLoginResult(accessToken, refreshToken, admin.isTotpEnrolled());
    }

    @Transactional
    public TotpSetupResponse setupTotp(Long adminId) {
        AdminAccount admin =
                adminAccountRepository
                        .findById(adminId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND));
        String secret = totpService.generateSecret();
        admin.enrollTotp(secret);
        log.info("관리자 TOTP 등록. adminId={}", adminId);
        return new TotpSetupResponse(secret, totpService.buildOtpAuthUri(secret, admin.getEmail()));
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

    private void validateActive(AdminAccount admin) {
        if (!admin.isActive()) {
            throw new CustomException(ErrorCode.ADMIN_ACCOUNT_SUSPENDED);
        }
    }

    private void validatePassword(String raw, String hash) {
        if (!passwordEncoder.matches(raw, hash)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    // TOTP 미등록이면 최초 로그인 허용(등록 유도), 등록됐으면 코드 검증
    private void validateTotp(AdminAccount admin, String totpCode) {
        if (admin.isTotpEnrolled() && !totpService.verify(admin.getTotpSecret(), totpCode)) {
            throw new CustomException(ErrorCode.INVALID_TOTP_CODE);
        }
    }
}
