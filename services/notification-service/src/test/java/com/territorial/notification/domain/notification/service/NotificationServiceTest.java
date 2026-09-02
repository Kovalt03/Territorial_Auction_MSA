package com.territorial.notification.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.notification.domain.notification.dto.NotificationListResponse;
import com.territorial.notification.domain.notification.entity.NotificationLog;
import com.territorial.notification.domain.notification.entity.NotificationType;
import com.territorial.notification.domain.notification.repository.NotificationLogRepository;
import com.territorial.notification.event.NotificationBadgePublisher;
import com.territorial.notification.global.exception.ErrorCode;
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
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks private NotificationService notificationService;

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private NotificationBadgePublisher badgePublisher;
    @Mock private ValueOperations<String, String> valueOperations;

    private NotificationLog log;

    @BeforeEach
    void setUp() {
        log =
                NotificationLog.builder()
                        .userId(1L)
                        .type(NotificationType.OUTBID)
                        .message("상위 입찰 발생")
                        .build();
        ReflectionTestUtils.setField(log, "id", 10L);
    }

    @Nested
    @DisplayName("persist")
    class Persist {

        @Test
        @DisplayName("알림 저장 → 미읽음 증가 + 배지 발행")
        void persist_success() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(notificationLogRepository.save(any(NotificationLog.class))).willReturn(log);

            notificationService.persist(1L, NotificationType.OUTBID, "상위 입찰 발생");

            verify(notificationLogRepository).save(any(NotificationLog.class));
            verify(valueOperations).increment("notification:unread:1");
            verify(badgePublisher).publish(eq(1L), any());
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsRead {

        @Test
        @DisplayName("정상 읽음 처리 → 미읽음 감소")
        void markAsRead_success() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(notificationLogRepository.findByIdAndUserId(10L, 1L))
                    .willReturn(Optional.of(log));
            given(valueOperations.decrement(anyString())).willReturn(0L);

            notificationService.markAsRead(1L, 10L);

            assertThat(log.isRead()).isTrue();
            verify(valueOperations).decrement("notification:unread:1");
        }

        @Test
        @DisplayName("이미 읽은 알림 → 조기 반환, 미읽음 미변경")
        void markAsRead_alreadyRead() {
            log.markAsRead();
            given(notificationLogRepository.findByIdAndUserId(10L, 1L))
                    .willReturn(Optional.of(log));

            notificationService.markAsRead(1L, 10L);

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("존재하지 않는 알림 → NOTIFICATION_NOT_FOUND")
        void markAsRead_notFound() {
            given(notificationLogRepository.findByIdAndUserId(10L, 1L))
                    .willReturn(Optional.empty());
            given(notificationLogRepository.existsById(10L)).willReturn(false);

            assertThatThrownBy(() -> notificationService.markAsRead(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("타인의 알림 → NOTIFICATION_FORBIDDEN")
        void markAsRead_forbidden() {
            given(notificationLogRepository.findByIdAndUserId(10L, 1L))
                    .willReturn(Optional.empty());
            given(notificationLogRepository.existsById(10L)).willReturn(true);

            assertThatThrownBy(() -> notificationService.markAsRead(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("getNotifications")
    class GetNotifications {

        @Test
        @DisplayName("목록 + 캐시된 미읽음 수 반환")
        void getNotifications_success() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("notification:unread:1")).willReturn("3");
            Slice<NotificationLog> slice = new SliceImpl<>(List.of(log));
            given(
                            notificationLogRepository.findByUserIdOrderByCreatedAtDesc(
                                    eq(1L), any(PageRequest.class)))
                    .willReturn(slice);

            NotificationListResponse response =
                    notificationService.getNotifications(1L, PageRequest.of(0, 20));

            assertThat(response.unreadCount()).isEqualTo(3);
            assertThat(response.notifications()).hasSize(1);
            assertThat(response.notifications().get(0).notificationId()).isEqualTo(10L);
        }
    }
}
