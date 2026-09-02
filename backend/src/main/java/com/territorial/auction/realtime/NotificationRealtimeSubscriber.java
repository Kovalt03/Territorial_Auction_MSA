package com.territorial.auction.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * notification-service의 notification.badge(Redis)를 구독해 WebSocket(/sub/user/{userId}/notification)으로
 * 브로드캐스트. 실시간 허브는 모놀리식 소유.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRealtimeSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void subscribe() {
        redissonClient
                .getTopic("notification.badge")
                .addListener(String.class, (channel, json) -> handle(json));
        log.info(
                "[NotificationRealtime] 구독 시작: notification.badge → /sub/user/{userId}/notification");
    }

    private void handle(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            long userId = node.get("userId").asLong();
            messagingTemplate.convertAndSend(
                    "/sub/user/" + userId + "/notification", node.get("payload"));
        } catch (Exception e) {
            log.error("[NotificationRealtime] 처리 실패: {}", json, e);
        }
    }
}
