package com.territorial.auction.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * social-service의 chat.message(Redis)를 구독해 WebSocket(/sub/chat/{roomId})으로 브로드캐스트. 실시간 허브는 모놀리식 소유.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void subscribe() {
        redissonClient
                .getTopic("chat.message")
                .addListener(String.class, (channel, json) -> handle(json));
        log.info("[ChatRealtime] 구독 시작: chat.message → /sub/chat/{roomId}");
    }

    private void handle(String json) {
        try {
            ChatBroadcast msg = objectMapper.readValue(json, ChatBroadcast.class);
            messagingTemplate.convertAndSend("/sub/chat/" + msg.roomId(), msg);
        } catch (Exception e) {
            log.error("[ChatRealtime] 처리 실패: {}", json, e);
        }
    }

    private record ChatBroadcast(
            Long messageId,
            String roomId,
            Long senderId,
            String senderNickname,
            String content,
            String sentAt) {}
}
