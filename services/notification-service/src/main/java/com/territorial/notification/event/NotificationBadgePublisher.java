package com.territorial.notification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.notification.domain.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/** 신규 알림 배지를 모놀리식 realtime 허브로 전달(Redis pub/sub) — WebSocket fan-out은 저지연 유지. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationBadgePublisher {

    public static final String TOPIC = "notification.badge";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public void publish(Long userId, NotificationResponse payload) {
        try {
            NotificationBadgeEvent event = new NotificationBadgeEvent(userId, payload);
            redissonClient.getTopic(TOPIC).publish(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("[NotificationBadge] 직렬화 실패 userId={}", userId, e);
        }
    }

    public record NotificationBadgeEvent(Long userId, NotificationResponse payload) {}
}
