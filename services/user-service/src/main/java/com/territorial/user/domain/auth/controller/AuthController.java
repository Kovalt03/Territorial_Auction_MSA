package com.territorial.user.domain.auth.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.user.domain.auth.dto.LoginRequest;
import com.territorial.user.domain.auth.dto.SignupRequest;
import com.territorial.user.domain.auth.dto.SignupResponse;
import com.territorial.user.domain.auth.dto.TokenPair;
import com.territorial.user.domain.auth.dto.TokenResponse;
import com.territorial.user.domain.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE = 60 * 60 * 24 * 14;
    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestBody @Valid SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request, HttpServletResponse response) {
        TokenPair tokenPair = authService.login(request);
        setRefreshTokenCookie(response, tokenPair.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(tokenPair.accessToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE) String refreshToken,
            HttpServletResponse response) {
        TokenPair tokenPair = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, tokenPair.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(tokenPair.accessToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal Long userId, HttpServletResponse response) {
        authService.logout(userId);
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<Void>> checkUsername(
            @RequestParam @NotBlank @Size(min = 4, max = 20) String username) {
        authService.checkUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Void>> checkEmail(
            @RequestParam @NotBlank @Email String email) {
        authService.checkEmail(email);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<Void>> checkNickname(
            @RequestParam @NotBlank @Size(min = 2, max = 20) String nickname) {
        authService.checkNickname(nickname);
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
