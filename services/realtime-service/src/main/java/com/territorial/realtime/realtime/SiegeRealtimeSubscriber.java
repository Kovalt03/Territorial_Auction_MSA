package com.territorial.realtime.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 모놀 combat 브리지가 발행하는 siege.alert(Redis)를 구독해 WebSocket(/sub/user/{userId}/siege-alert)으로 브로드캐스트.
 * 봉투는 {userId, payload} 형태.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiegeRealtimeSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void subscribe() {
        redissonClient
                .getTopic("siege.alert")
                .addListener(String.class, (channel, json) -> handle(json));
        log.info("[SiegeRealtime] 구독 시작: siege.alert → /sub/user/{userId}/siege-alert");
    }

    private void handle(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            long userId = node.get("userId").asLong();
            messagingTemplate.convertAndSend(
                    "/sub/user/" + userId + "/siege-alert", node.get("payload"));
        } catch (Exception e) {
            log.error("[SiegeRealtime] 처리 실패: {}", json, e);
        }
    }
}
