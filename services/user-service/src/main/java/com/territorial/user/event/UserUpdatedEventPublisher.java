package com.territorial.user.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserUpdatedEventPublisher {

    public static final String TOPIC = "user.updated";
    private final UserOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void enqueue(UserUpdatedEvent event) {
        try {
            outboxEventRepository.save(
                    new UserOutboxEvent(TOPIC, objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("user.updated 이벤트 직렬화 실패", e);
        }
    }
}
