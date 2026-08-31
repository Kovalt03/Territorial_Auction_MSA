package com.territorial.user.domain.auth.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.auth.dto.LoginRequest;
import com.territorial.user.domain.auth.dto.SignupRequest;
import com.territorial.user.domain.auth.dto.SignupResponse;
import com.territorial.user.domain.auth.dto.TokenPair;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.event.UserCreatedEvent;
import com.territorial.user.event.UserCreatedEventPublisher;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserCreatedEventPublisher userCreatedEventPublisher;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateUnique(request);
        User user = userRepository.save(newUser(request));
        walletRepository.save(Wallet.builder().user(user).build());
        userCreatedEventPublisher.enqueue(
                new UserCreatedEvent(
                        user.getId(), user.getUsername(), user.getEmail(), user.getNickname()));
        return SignupResponse.from(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        User user = findByEmailOrThrow(request.email());
        validateLoginStatus(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokenPair(user);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        Long userId = parseRefreshUserId(refreshToken);
        if (!refreshTokenService.isValid(userId, refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        validateLoginStatus(user);
        return issueTokenPair(user);
    }

    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }

    public void checkUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
        }
    }

    public void checkEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    public void checkNickname(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
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

    private void validateLoginStatus(User user) {
        if ("WITHDRAWN".equals(user.getStatus())) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        if ("SUSPENDED".equals(user.getStatus())) {
            throw new CustomException(ErrorCode.SUSPENDED_USER);
        }
    }

    private Long parseRefreshUserId(String refreshToken) {
        try {
            return jwtTokenProvider.getRefreshTokenUserId(refreshToken);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}
