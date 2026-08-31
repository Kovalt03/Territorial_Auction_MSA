package com.territorial.auction.domain.auth.service;

import com.territorial.auction.domain.auth.dto.*;
import com.territorial.auction.domain.user.entity.*;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TokenPair login(LoginRequest request) {
        // email로 조회
        User user =
                userRepository
                        .findByEmail(request.email())
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        // 유저 상태 검증
        if (user.getStatus() == UserStatus.WITHDRAWN)
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        if (user.getStatus() == UserStatus.SUSPENDED)
            throw new CustomException(ErrorCode.SUSPENDED_USER);

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()))
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);

        // 관리자 계정은 강화된 관리자 로그인(/api/v1/admin/auth/login)만 허용
        if (user.isAdmin()) throw new CustomException(ErrorCode.ADMIN_LOGIN_REQUIRED);

        // Access/Refresh 토큰 발급 (role 포함)
        String accessToken =
                jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        // Redis 저장
        refreshTokenService.save(user.getId(), refreshToken);

        return new TokenPair(accessToken, refreshToken);
    }

    public TokenPair refresh(String refreshToken) {
        // refreshToken 파싱
        Long userId = jwtTokenProvider.getRefreshTokenUserId(refreshToken);

        // Redis 검증
        if (!refreshTokenService.isValid(userId, refreshToken))
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);

        // role 유지를 위해 유저 조회 후 토큰 재발급
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String accessToken = jwtTokenProvider.createAccessToken(userId, user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        // Redis 갱신
        refreshTokenService.save(userId, newRefreshToken);

        return new TokenPair(accessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        // Redis에서 refreshToken 삭제
        refreshTokenService.delete(userId);
    }

    public void checkNickname(String nickname) {
        if (userRepository.existsByNickname(nickname))
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
    }

    public void checkEmail(String email) {
        if (userRepository.existsByEmail(email))
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
    }

    public void checkUsername(String username) {
        if (userRepository.existsByUsername(username))
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
    }
}
