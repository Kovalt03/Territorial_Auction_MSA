package com.territorial.auction.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 알림 발행 파사드. 저장·조회·실시간 배지는 notification-service가 소유하며, 모놀리식은 각 도메인의 알림 요청을
 * Kafka(notification-events)로 넘긴다. 호출부는 기존 sendNotification 시그니처를 그대로 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String TOPIC = "notification-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendNotification(Long userId, NotificationType type, String message) {
        try {
            String payload =
                    objectMapper.writeValueAsString(
                            new NotificationRequested(userId, type.name(), message));
            kafkaTemplate.send(TOPIC, String.valueOf(userId), payload);
        } catch (JsonProcessingException e) {
            log.error("[Notification] 직렬화 실패 userId={}, type={}", userId, type, e);
        }
    }

    private record NotificationRequested(Long userId, String type, String message) {}
}
