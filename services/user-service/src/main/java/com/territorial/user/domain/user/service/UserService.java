package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.AdminUserCountsResponse;
import com.territorial.user.domain.user.dto.AdminUserPageResponse;
import com.territorial.user.domain.user.dto.AdminUserView;
import com.territorial.user.domain.user.dto.ChangeNicknameResponse;
import com.territorial.user.domain.user.dto.NotificationSettingResponse;
import com.territorial.user.domain.user.dto.UpdateNotificationSettingRequest;
import com.territorial.user.domain.user.dto.UserNicknameResponse;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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

    // 표시용 닉네임 배치 조회 — ranking-service 등이 userId 목록으로 조회. 목록 조회라 없으면 빈 리스트.
    public List<UserNicknameResponse> getNicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(userIds).stream()
                .map(UserNicknameResponse::from)
                .toList();
    }

    // 관리자 유저 검색(admin-service 위임). status blank→전체, keyword blank→전체("%%").
    // keyword는 절대 null로 넘기지 않는다([[gotcha_lower_bytea_null_param]]).
    public AdminUserPageResponse searchUsersForAdmin(
            String status, String keyword, Pageable pageable) {
        String statusFilter = StringUtils.hasText(status) ? status : null;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : "";
        return AdminUserPageResponse.from(
                userRepository.searchForAdmin(statusFilter, kw, pageable));
    }

    // 관리자 단건 조회(admin-service 위임). 없으면 예외.
    public AdminUserView getUserView(Long userId) {
        return AdminUserView.from(findUserOrThrow(userId));
    }

    // 관리자 유저 존재 검증(admin-service 위임). 지급·활동 대상 검증에 사용.
    public boolean existsUser(Long userId) {
        return userRepository.existsById(userId);
    }

    // 관리자 배치 조회(admin-service 위임). 목록 조회라 없으면 빈 리스트.
    public List<AdminUserView> findUserViews(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(userIds).stream().map(AdminUserView::from).toList();
    }

    // 관리자 대시보드 유저 집계(admin-service 위임).
    public AdminUserCountsResponse getUserCounts() {
        return new AdminUserCountsResponse(
                userRepository.count(),
                userRepository.countByStatus("ACTIVE"),
                userRepository.countByStatus("SUSPENDED"));
    }

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
