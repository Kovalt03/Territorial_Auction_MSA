package com.territorial.auction.domain.notification.service;

import com.territorial.auction.domain.notification.dto.NotificationListResponse;
import com.territorial.auction.domain.notification.dto.NotificationResponse;
import com.territorial.auction.domain.notification.entity.NotificationLog;
import com.territorial.auction.domain.notification.entity.NotificationLog.NotificationType;
import com.territorial.auction.domain.notification.repository.NotificationLogRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final String UNREAD_KEY = "notification:unread:";

    private final NotificationLogRepository notificationLogRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationListResponse getNotifications(Long userId, Pageable pageable) {
        Slice<NotificationLog> slice =
                notificationLogRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);

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

    private NotificationLog validateOwnership(Long notificationId, Long userId) {
        return notificationLogRepository
                .findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(
                        () -> {
                            boolean exists = notificationLogRepository.existsById(notificationId);
                            return exists
                                    ? new CustomException(ErrorCode.NOTIFICATION_FORBIDDEN)
                                    : new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND);
                        });
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationLogRepository.markAllAsReadByUserId(userId);
        if (updated > 0) {
            redisTemplate.opsForValue().set(UNREAD_KEY + userId, 0);
        }
    }

    @Transactional
    public void sendNotification(Long userId, NotificationType type, String message) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        NotificationLog saved =
                notificationLogRepository.save(
                        NotificationLog.builder().user(user).type(type).message(message).build());

        redisTemplate.opsForValue().increment(UNREAD_KEY + userId);

        NotificationResponse payload = NotificationResponse.from(saved);
        messagingTemplate.convertAndSend("/sub/user/" + userId + "/notification", payload);
        log.info("알림 발송. userId={}, type={}", userId, type);
    }

    private long getUnreadCount(Long userId) {
        Object cached = redisTemplate.opsForValue().get(UNREAD_KEY + userId);
        if (cached != null) {
            return Long.parseLong(cached.toString());
        }
        long count = notificationLogRepository.countByUser_IdAndIsReadFalse(userId);
        redisTemplate.opsForValue().set(UNREAD_KEY + userId, count);
        return count;
    }

    private void decrementUnreadCount(Long userId) {
        Long current = redisTemplate.opsForValue().decrement(UNREAD_KEY + userId);
        if (current != null && current < 0) {
            redisTemplate.opsForValue().set(UNREAD_KEY + userId, 0);
        }
    }
}
