package com.territorial.auction.domain.auth.controller;

import com.territorial.auction.domain.auth.dto.*;
import com.territorial.auction.domain.auth.service.AuthService;
import com.territorial.auction.global.common.ApiResponse;
import com.territorial.auction.global.validation.ValidNickname;
import com.territorial.auction.global.validation.ValidUsername;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE = 60 * 60 * 24 * 14; // 14일

    private final AuthService authService;

    // POST /api/v1/auth/signup
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestBody @Valid SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    // POST /api/v1/auth/login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request, HttpServletResponse response) {
        TokenPair tokenPair = authService.login(request);
        setRefreshTokenCookie(response, tokenPair.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(tokenPair.accessToken())));
    }

    // POST /api/v1/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE) String refreshToken,
            HttpServletResponse response) {
        TokenPair tokenPair = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, tokenPair.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(tokenPair.accessToken())));
    }

    // POST /api/v1/auth/logout (Authorization: Bearer {accessToken} 필요)
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal Long userId, HttpServletResponse response) {
        authService.logout(userId);
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // GET /api/v1/auth/check-nickname?nickname={nickname}
    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<Void>> checkNickname(
            @RequestParam @ValidNickname String nickname) {
        authService.checkNickname(nickname);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // GET GET /api/v1/auth/check-email?email={email}
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Void>> checkEmail(
            @RequestParam @NotBlank @Email String email) {
        authService.checkEmail(email);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // GET /api/v1/auth/check-username?username={username}
    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<Void>> checkUsername(
            @RequestParam @ValidUsername String username) {
        authService.checkUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
