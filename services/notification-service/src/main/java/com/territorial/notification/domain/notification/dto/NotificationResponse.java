package com.territorial.notification.domain.notification.dto;

import com.territorial.notification.domain.notification.entity.NotificationLog;
import com.territorial.notification.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String message,
        boolean isRead,
        LocalDateTime createdAt) {
    public static NotificationResponse from(NotificationLog log) {
        return new NotificationResponse(
                log.getId(), log.getType(), log.getMessage(), log.isRead(), log.getCreatedAt());
    }
}
