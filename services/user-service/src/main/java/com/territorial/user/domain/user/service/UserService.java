package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.ChangeNicknameResponse;
import com.territorial.user.domain.user.dto.NotificationSettingResponse;
import com.territorial.user.domain.user.dto.UpdateNotificationSettingRequest;
import com.territorial.user.domain.user.entity.NotificationSetting;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.repository.NotificationSettingRepository;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.event.UserStatusChangedEvent;
import com.territorial.user.event.UserStatusChangedEventPublisher;
import com.territorial.user.event.UserUpdatedEvent;
import com.territorial.user.event.UserUpdatedEventPublisher;
import com.territorial.user.global.exception.ErrorCode;
import com.territorial.user.global.security.JwtTokenProvider;
import com.territorial.user.global.security.RefreshTokenService;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 신원 프로필·상태 쓰기(닉네임·비밀번호·탈퇴/정지) + 알림 설정. user-service가 User·설정을 소유한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    // 모놀리식·user-service JWT 필터가 공유하는 블랙리스트 키. 반드시 동일 문자열.
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserUpdatedEventPublisher userUpdatedEventPublisher;
    private final UserStatusChangedEventPublisher userStatusChangedEventPublisher;
    private final NotificationSettingRepository notificationSettingRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    /** 셀프 탈퇴. 상태=WITHDRAWN → 로그인 즉시 차단, refresh 삭제, access 토큰 블랙리스트(공유). */
    @Transactional
    public void deleteMe(Long userId, String password, String accessToken) {
        User user = findUserOrThrow(userId);
        if (!user.getPasswordHash().isEmpty()
                && !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }
        applyStatus(user, STATUS_WITHDRAWN);
        refreshTokenService.delete(userId);
        blacklistAccessToken(accessToken);
    }

    /** 관리자 상태 변경(정지·탈퇴·복구). 모놀리식 admin이 내부 호출. 갱신 차단 위해 refresh 삭제. */
    @Transactional
    public void changeStatus(Long userId, String status) {
        applyStatus(findUserOrThrow(userId), status);
        refreshTokenService.delete(userId);
    }

    private void applyStatus(User user, String status) {
        user.updateStatus(status);
        // 프로젝션(admin 목록/표시)과 로그인 차단 정합을 위해 전파.
        userStatusChangedEventPublisher.enqueue(new UserStatusChangedEvent(user.getId(), status));
    }

    private void blacklistAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        long remainingMs = jwtTokenProvider.getRemainingMs(accessToken);
        if (remainingMs <= 0) {
            return;
        }
        redisTemplate
                .opsForValue()
                .set(BLACKLIST_KEY_PREFIX + accessToken, "1", Duration.ofMillis(remainingMs));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public NotificationSettingResponse getNotificationSetting(Long userId) {
        return NotificationSettingResponse.from(findSettingOrThrow(userId));
    }

    @Transactional
    public NotificationSettingResponse updateNotificationSetting(
            Long userId, UpdateNotificationSettingRequest request) {
        NotificationSetting setting = findSettingOrThrow(userId);
        setting.update(
                request.isOutbidEnabled(),
                request.isAuctionStartEnabled(),
                request.isMarketingEnabled());
        return NotificationSettingResponse.from(setting);
    }

    private NotificationSetting findSettingOrThrow(Long userId) {
        return notificationSettingRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Transactional
    public ChangeNicknameResponse changeNickname(Long userId, String nickname) {
        User user = findUserOrThrow(userId);
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.updateNickname(nickname);
        // 닉네임은 모놀리식 프로젝션과 15개 도메인 표시에 쓰이므로 변경을 전파한다.
        userUpdatedEventPublisher.enqueue(new UserUpdatedEvent(user.getId(), nickname));
        return new ChangeNicknameResponse(user.getId(), user.getNickname(), LocalDateTime.now());
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findUserOrThrow(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
    }
}
