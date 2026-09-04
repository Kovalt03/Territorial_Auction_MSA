package com.territorial.admin.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * admin → notification-service 알림 발행(Kafka notification-events). type은 notification-service
 * NotificationType 이름과 일치해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationClient {

    public static final String TOPIC = "notification-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendNotification(Long userId, String type, String message) {
        try {
            String payload =
                    objectMapper.writeValueAsString(
                            new NotificationRequested(userId, type, message));
            kafkaTemplate.send(TOPIC, String.valueOf(userId), payload);
        } catch (JsonProcessingException e) {
            log.error("[Notification] 직렬화 실패 userId={}, type={}", userId, type, e);
        }
    }

    private record NotificationRequested(Long userId, String type, String message) {}
}
