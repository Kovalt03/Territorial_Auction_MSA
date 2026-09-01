package com.territorial.user.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserStatusChangedEventPublisher {

    public static final String TOPIC = "user.status-changed";
    private final UserOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void enqueue(UserStatusChangedEvent event) {
        try {
            outboxEventRepository.save(
                    new UserOutboxEvent(TOPIC, objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("user.status-changed 이벤트 직렬화 실패", e);
        }
    }
}
