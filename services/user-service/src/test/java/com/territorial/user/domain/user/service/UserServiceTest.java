package com.territorial.user.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.ChangeNicknameResponse;
import com.territorial.user.domain.user.dto.NotificationSettingResponse;
import com.territorial.user.domain.user.dto.UpdateNotificationSettingRequest;
import com.territorial.user.domain.user.entity.NotificationSetting;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.repository.NotificationSettingRepository;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.event.UserUpdatedEvent;
import com.territorial.user.event.UserStatusChangedEvent;
import com.territorial.user.event.UserStatusChangedEventPublisher;
import com.territorial.user.event.UserUpdatedEventPublisher;
import com.territorial.user.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserUpdatedEventPublisher userUpdatedEventPublisher;
    @Mock private UserStatusChangedEventPublisher userStatusChangedEventPublisher;
    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private com.territorial.user.global.security.RefreshTokenService refreshTokenService;
    @Mock private com.territorial.user.global.security.JwtTokenProvider jwtTokenProvider;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private User user(long id, String passwordHash) {
        User user =
                User.builder()
                        .username("u" + id)
                        .email(id + "@example.com")
                        .passwordHash(passwordHash)
                        .nickname("닉" + id)
                        .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void changeNicknameUpdatesAndPublishesEvent() {
        User user = user(1L, "hash");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("새닉")).willReturn(false);

        ChangeNicknameResponse response = userService.changeNickname(1L, "새닉");

        assertThat(response.nickname()).isEqualTo("새닉");
        assertThat(user.getNickname()).isEqualTo("새닉");
        verify(userUpdatedEventPublisher).enqueue(any(UserUpdatedEvent.class));
    }

    @Test
    void changeNicknameRejectsDuplicate() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "hash")));
        given(userRepository.existsByNickname("중복")).willReturn(true);

        assertThatThrownBy(() -> userService.changeNickname(1L, "중복"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        verify(userUpdatedEventPublisher, never()).enqueue(any());
    }

    @Test
    void changePasswordUpdatesWhenCurrentMatches() {
        User user = user(1L, "oldHash");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("old", "oldHash")).willReturn(true);
        given(passwordEncoder.encode("new")).willReturn("newHash");

        userService.changePassword(1L, "old", "new");

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
    }

    @Test
    void changePasswordRejectsWrongCurrent() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "oldHash")));
        given(passwordEncoder.matches("wrong", "oldHash")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, "wrong", "new"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
    }

    @Test
    void updateNotificationSettingAppliesChanges() {
        NotificationSetting setting = NotificationSetting.builder().user(user(1L, "h")).build();
        given(notificationSettingRepository.findById(1L)).willReturn(Optional.of(setting));

        NotificationSettingResponse response =
                userService.updateNotificationSetting(
                        1L, new UpdateNotificationSettingRequest(false, null, true));

        assertThat(response.isOutbidEnabled()).isFalse(); // 변경됨
        assertThat(response.isAuctionStartEnabled()).isTrue(); // null → 유지
        assertThat(response.isMarketingEnabled()).isTrue(); // 변경됨
    }

    @Test
    void getNotificationSettingRejectsMissing() {
        given(notificationSettingRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getNotificationSetting(9L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void deleteMeWithdrawsAndPublishesStatus() {
        User user = user(1L, "hash");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pw", "hash")).willReturn(true);

        userService.deleteMe(1L, "pw", null); // accessToken null → 블랙리스트 스킵

        assertThat(user.getStatus()).isEqualTo("WITHDRAWN");
        verify(refreshTokenService).delete(1L);
        verify(userStatusChangedEventPublisher).enqueue(any(UserStatusChangedEvent.class));
    }

    @Test
    void deleteMeRejectsWrongPassword() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "hash")));
        given(passwordEncoder.matches("wrong", "hash")).willReturn(false);

        assertThatThrownBy(() -> userService.deleteMe(1L, "wrong", null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
        verify(refreshTokenService, never()).delete(any());
    }

    @Test
    void changeStatusAppliesAndPublishes() {
        User user = user(1L, "hash");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.changeStatus(1L, "SUSPENDED");

        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        verify(refreshTokenService).delete(1L);
        verify(userStatusChangedEventPublisher).enqueue(any(UserStatusChangedEvent.class));
    }
}
