package com.territorial.admin.domain.admin.controller;

import com.territorial.admin.domain.admin.dto.AdminLoginRequest;
import com.territorial.admin.domain.admin.dto.AdminLoginResponse;
import com.territorial.admin.domain.admin.dto.AdminLoginResult;
import com.territorial.admin.domain.admin.dto.TotpSetupResponse;
import com.territorial.admin.domain.admin.service.AdminAuthService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String REFRESH_TOKEN_COOKIE = "adminRefreshToken";
    private static final String REFRESH_TOKEN_PATH = "/api/v1/admin/auth";
    private static final int REFRESH_TOKEN_MAX_AGE = 60 * 60 * 24 * 14; // 14일

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(
            @RequestBody @Valid AdminLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AdminLoginResult result = adminAuthService.login(request, resolveClientIp(httpRequest));
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(
                ApiResponse.ok(
                        new AdminLoginResponse(result.accessToken(), result.totpEnrolled())));
    }

    @PostMapping("/totp/setup")
    public ResponseEntity<ApiResponse<TotpSetupResponse>> setupTotp(
            @AuthenticationPrincipal Long adminId) {
        return ResponseEntity.ok(ApiResponse.ok(adminAuthService.setupTotp(adminId)));
    }

    // 게이트웨이가 붙인 실제 클라이언트 IP(X-Forwarded-For 첫 홉)를 사용한다.
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(REFRESH_TOKEN_PATH);
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        response.addCookie(cookie);
    }
}
