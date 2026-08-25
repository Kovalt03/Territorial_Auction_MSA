package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminLoginRequest;
import com.territorial.auction.domain.admin.dto.AdminLoginResult;
import com.territorial.auction.domain.admin.dto.TotpSetupResponse;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserStatus;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.AdminSecurityProperties;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import com.territorial.auction.global.security.totp.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final TotpService totpService;
    private final AdminSecurityProperties adminSecurityProperties;

    @Transactional
    public AdminLoginResult login(AdminLoginRequest request, String clientIp) {
        validateIpAllowed(clientIp);
        User user = findAdminByEmailOrThrow(request.email());
        validateActive(user);
        validatePassword(request.password(), user.getPasswordHash());
        validateTotp(user, request.totpCode());

        String accessToken =
                jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);

        log.info(
                "관리자 로그인 성공. adminUserId={}, totpEnrolled={}", user.getId(), user.isTotpEnrolled());
        return new AdminLoginResult(accessToken, refreshToken, user.isTotpEnrolled());
    }

    @Transactional
    public TotpSetupResponse setupTotp(Long userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isAdmin()) {
            throw new CustomException(ErrorCode.NOT_ADMIN_ACCOUNT);
        }
        String secret = totpService.generateSecret();
        user.enrollTotp(secret);
        log.info("관리자 TOTP 등록. adminUserId={}", userId);
        return new TotpSetupResponse(secret, totpService.buildOtpAuthUri(secret, user.getEmail()));
    }

    private void validateIpAllowed(String clientIp) {
        if (!adminSecurityProperties.isIpAllowed(clientIp)) {
            log.warn("관리자 로그인 차단 - 허용되지 않은 IP. ip={}", clientIp);
            throw new CustomException(ErrorCode.ADMIN_IP_NOT_ALLOWED);
        }
    }

    private User findAdminByEmailOrThrow(String email) {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
        if (!user.isAdmin()) {
            throw new CustomException(ErrorCode.NOT_ADMIN_ACCOUNT);
        }
        return user;
    }

    private void validateActive(User user) {
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.SUSPENDED_USER);
        }
    }

    private void validatePassword(String raw, String hash) {
        if (!passwordEncoder.matches(raw, hash)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    // TOTP 미등록이면 최초 로그인 허용(등록 유도), 등록됐으면 코드 검증
    private void validateTotp(User user, String totpCode) {
        if (user.isTotpEnrolled() && !totpService.verify(user.getTotpSecret(), totpCode)) {
            throw new CustomException(ErrorCode.INVALID_TOTP_CODE);
        }
    }
}
