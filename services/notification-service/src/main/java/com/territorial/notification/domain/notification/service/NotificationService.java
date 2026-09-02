package com.territorial.notification.domain.notification.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.notification.domain.notification.dto.NotificationListResponse;
import com.territorial.notification.domain.notification.dto.NotificationResponse;
import com.territorial.notification.domain.notification.entity.NotificationLog;
import com.territorial.notification.domain.notification.entity.NotificationType;
import com.territorial.notification.domain.notification.repository.NotificationLogRepository;
import com.territorial.notification.event.NotificationBadgePublisher;
import com.territorial.notification.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final String UNREAD_KEY = "notification:unread:";

    private final NotificationLogRepository notificationLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final NotificationBadgePublisher badgePublisher;

    public NotificationListResponse getNotifications(Long userId, Pageable pageable) {
        Slice<NotificationLog> slice =
                notificationLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        long unreadCount = getUnreadCount(userId);
        List<NotificationResponse> notifications =
                slice.getContent().stream().map(NotificationResponse::from).toList();

        return new NotificationListResponse(unreadCount, notifications);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        NotificationLog log = validateOwnership(notificationId, userId);
        if (log.isRead()) {
            return;
        }
        log.markAsRead();
        decrementUnreadCount(userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationLogRepository.markAllAsReadByUserId(userId);
        if (updated > 0) {
            redisTemplate.opsForValue().set(UNREAD_KEY + userId, "0");
        }
    }

    /** Kafka notification-events 구독 처리 — 알림 저장 + 미읽음 증가 + 실시간 배지 발행. */
    @Transactional
    public void persist(Long userId, NotificationType type, String message) {
        NotificationLog saved =
                notificationLogRepository.save(
                        NotificationLog.builder()
                                .userId(userId)
                                .type(type)
                                .message(message)
                                .build());

        redisTemplate.opsForValue().increment(UNREAD_KEY + userId);
        badgePublisher.publish(userId, NotificationResponse.from(saved));
        log.info("알림 저장. userId={}, type={}", userId, type);
    }

    private NotificationLog validateOwnership(Long notificationId, Long userId) {
        return notificationLogRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(
                        () -> {
                            boolean exists = notificationLogRepository.existsById(notificationId);
                            return exists
                                    ? new CustomException(ErrorCode.NOTIFICATION_FORBIDDEN)
                                    : new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND);
                        });
    }

    private long getUnreadCount(Long userId) {
        String cached = redisTemplate.opsForValue().get(UNREAD_KEY + userId);
        if (cached != null) {
            return Long.parseLong(cached);
        }
        long count = notificationLogRepository.countByUserIdAndIsReadFalse(userId);
        redisTemplate.opsForValue().set(UNREAD_KEY + userId, String.valueOf(count));
        return count;
    }

    private void decrementUnreadCount(Long userId) {
        Long current = redisTemplate.opsForValue().decrement(UNREAD_KEY + userId);
        if (current != null && current < 0) {
            redisTemplate.opsForValue().set(UNREAD_KEY + userId, "0");
        }
    }
}
