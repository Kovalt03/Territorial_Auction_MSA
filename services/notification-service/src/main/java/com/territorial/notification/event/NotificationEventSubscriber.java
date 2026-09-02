package com.territorial.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.notification.domain.notification.entity.NotificationType;
import com.territorial.notification.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** 각 도메인이 발행한 알림 요청(Kafka notification-events)을 구독해 저장·실시간 배지로 전개한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventSubscriber {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(topics = "notification-events", groupId = "notification-persist")
    public void handle(@Payload String json) {
        try {
            NotificationRequested e = objectMapper.readValue(json, NotificationRequested.class);
            notificationService.persist(
                    e.userId(), NotificationType.valueOf(e.type()), e.message());
        } catch (Exception e) {
            log.error("[NotificationEvent] 처리 실패: payload={}", json, e);
            throw new IllegalStateException("notification-events 처리 실패", e);
        }
    }

    private record NotificationRequested(Long userId, String type, String message) {}
}
