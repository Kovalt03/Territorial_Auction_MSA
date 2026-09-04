package com.territorial.map.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * map → notification-service 알림 발행. 저장·실시간 배지는 notification-service가 소유하며, map은
 * notification-events(Kafka)로 요청만 넘긴다. type은 notification-service NotificationType 이름과 일치해야 한다.
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
