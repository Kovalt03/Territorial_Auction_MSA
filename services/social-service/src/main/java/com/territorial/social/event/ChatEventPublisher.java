package com.territorial.social.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.social.domain.social.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/** 채팅 메시지를 모놀리식 realtime 허브로 전달(Redis pub/sub) — WebSocket fan-out은 저지연 유지. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    public static final String TOPIC = "chat.message";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public void publish(ChatMessageResponse message) {
        try {
            redissonClient.getTopic(TOPIC).publish(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.error("[ChatEvent] 직렬화 실패 messageId={}", message.messageId(), e);
        }
    }
}
