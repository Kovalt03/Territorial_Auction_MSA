package com.territorial.auction.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.notification.dto.NotificationListResponse;
import com.territorial.auction.domain.notification.dto.NotificationResponse;
import com.territorial.auction.domain.notification.entity.NotificationLog;
import com.territorial.auction.domain.notification.entity.NotificationLog.NotificationType;
import com.territorial.auction.domain.notification.repository.NotificationLogRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks private NotificationService notificationService;

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private User user;
    private NotificationLog unreadLog;
    private NotificationLog readLog;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .username("user1")
                        .email("user1@test.com")
                        .passwordHash("hashed")
                        .nickname("테스터")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        unreadLog =
                NotificationLog.builder()
                        .user(user)
                        .type(NotificationType.OUTBID)
                        .message("상회 입찰이 발생했습니다.")
                        .build();
        ReflectionTestUtils.setField(unreadLog, "id", 10L);
        ReflectionTestUtils.setField(unreadLog, "createdAt", LocalDateTime.of(2026, 5, 11, 12, 0));

        readLog =
                NotificationLog.builder()
                        .user(user)
                        .type(NotificationType.AUCTION_WIN)
                        .message("경매에 낙찰되었습니다.")
                        .build();
        ReflectionTestUtils.setField(readLog, "id", 11L);
        ReflectionTestUtils.setField(readLog, "isRead", true);
        ReflectionTestUtils.setField(readLog, "createdAt", LocalDateTime.of(2026, 5, 10, 9, 0));
    }

    @Nested
    @DisplayName("getNotifications()")
    class GetNotifications {

        @Test
        @DisplayName("Redis 캐시 hit → DB 카운트 쿼리 없이 반환")
        void getNotifications_cacheHit() {
            Pageable pageable = PageRequest.of(0, 20);
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(
                            notificationLogRepository.findByUser_IdOrderByCreatedAtDesc(
                                    eq(1L), any(Pageable.class)))
                    .willReturn(new SliceImpl<>(List.of(unreadLog, readLog)));
            given(valueOps.get("notification:unread:1")).willReturn(3);

            NotificationListResponse response = notificationService.getNotifications(1L, pageable);

            assertThat(response.unreadCount()).isEqualTo(3);
            assertThat(response.notifications()).hasSize(2);
            then(notificationLogRepository).should(never()).countByUser_IdAndIsReadFalse(any());
        }

        @Test
        @DisplayName("Redis 캐시 miss → DB에서 카운트 조회 후 캐시 갱신")
        void getNotifications_cacheMiss() {
            Pageable pageable = PageRequest.of(0, 20);
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(
                            notificationLogRepository.findByUser_IdOrderByCreatedAtDesc(
                                    eq(1L), any(Pageable.class)))
                    .willReturn(new SliceImpl<>(List.of(unreadLog)));
            given(valueOps.get("notification:unread:1")).willReturn(null);
            given(notificationLogRepository.countByUser_IdAndIsReadFalse(1L)).willReturn(1L);

            NotificationListResponse response = notificationService.getNotifications(1L, pageable);

            assertThat(response.unreadCount()).isEqualTo(1);
            then(valueOps).should().set("notification:unread:1", 1L);
        }
    }

    @Nested
    @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test
        @DisplayName("읽지 않은 알림 → isRead=true, Redis DECR")
        void markAsRead_success() {
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(notificationLogRepository.findByIdAndUser_Id(10L, 1L))
                    .willReturn(Optional.of(unreadLog));
            given(valueOps.decrement("notification:unread:1")).willReturn(0L);

            notificationService.markAsRead(1L, 10L);

            assertThat(unreadLog.isRead()).isTrue();
        }

        @Test
        @DisplayName("이미 읽은 알림 → 변경 없음, Redis DECR 없음")
        void markAsRead_alreadyRead() {
            given(notificationLogRepository.findByIdAndUser_Id(11L, 1L))
                    .willReturn(Optional.of(readLog));

            notificationService.markAsRead(1L, 11L);

            then(valueOps).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("존재하지 않는 알림 → NOTIFICATION_NOT_FOUND")
        void markAsRead_notFound() {
            given(notificationLogRepository.findByIdAndUser_Id(99L, 1L))
                    .willReturn(Optional.empty());
            given(notificationLogRepository.existsById(99L)).willReturn(false);

            assertThatThrownBy(() -> notificationService.markAsRead(1L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("타인의 알림 → NOTIFICATION_FORBIDDEN")
        void markAsRead_forbidden() {
            given(notificationLogRepository.findByIdAndUser_Id(10L, 2L))
                    .willReturn(Optional.empty());
            given(notificationLogRepository.existsById(10L)).willReturn(true);

            assertThatThrownBy(() -> notificationService.markAsRead(2L, 10L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("markAllAsRead()")
    class MarkAllAsRead {

        @Test
        @DisplayName("읽지 않은 알림 존재 → DB 벌크 업데이트 + Redis 0으로 SET")
        void markAllAsRead_success() {
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(notificationLogRepository.markAllAsReadByUserId(1L)).willReturn(2);

            notificationService.markAllAsRead(1L);

            then(valueOps).should().set("notification:unread:1", 0);
        }

        @Test
        @DisplayName("읽지 않은 알림 없음 → Redis SET 호출 안 함")
        void markAllAsRead_nothingToUpdate() {
            given(notificationLogRepository.markAllAsReadByUserId(1L)).willReturn(0);

            notificationService.markAllAsRead(1L);

            then(valueOps).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("sendNotification()")
    class SendNotification {

        @Test
        @DisplayName("알림 저장 + Redis INCR + WebSocket 발송")
        void sendNotification_success() {
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(notificationLogRepository.save(any(NotificationLog.class))).willReturn(unreadLog);

            notificationService.sendNotification(1L, NotificationType.OUTBID, "상회 입찰이 발생했습니다.");

            then(valueOps).should().increment("notification:unread:1");
            then(messagingTemplate)
                    .should()
                    .convertAndSend(
                            eq("/sub/user/1/notification"), any(NotificationResponse.class));
        }

        @Test
        @DisplayName("존재하지 않는 userId → USER_NOT_FOUND")
        void sendNotification_userNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    notificationService.sendNotification(
                                            99L, NotificationType.OUTBID, "msg"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}
