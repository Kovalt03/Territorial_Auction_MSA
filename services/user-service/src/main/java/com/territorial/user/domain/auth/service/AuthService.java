package com.territorial.user.domain.auth.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.auth.dto.LoginRequest;
import com.territorial.user.domain.auth.dto.SignupRequest;
import com.territorial.user.domain.auth.dto.SignupResponse;
import com.territorial.user.domain.auth.dto.TokenPair;
import com.territorial.user.domain.user.entity.GlobalVault;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.GlobalVaultRepository;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.global.exception.ErrorCode;
import com.territorial.user.global.security.JwtTokenProvider;
import com.territorial.user.global.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateUnique(request);
        User user = userRepository.save(newUser(request));
        walletRepository.save(Wallet.builder().user(user).build());
        globalVaultRepository.save(GlobalVault.builder().user(user).build());
        return SignupResponse.from(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        User user = findByEmailOrThrow(request.email());
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokenPair(user);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        if (!refreshTokenService.isValid(userId, refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return issueTokenPair(
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)));
    }

    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }

    private TokenPair issueTokenPair(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);
        return new TokenPair(accessToken, refreshToken);
    }

    private User newUser(SignupRequest request) {
        return User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();
    }

    private User findByEmailOrThrow(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
    }

    private void validateUnique(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }
}
