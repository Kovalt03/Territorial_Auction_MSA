package com.territorial.auction.domain.user.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.user.service.UserProjectionService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProjectionSubscriber {

    private static final String UPDATED_TOPIC = "user.updated";
    private static final String STATUS_CHANGED_TOPIC = "user.status-changed";

    private final ObjectMapper objectMapper;
    private final UserProjectionService projectionService;

    @KafkaListener(topics = "user-events", groupId = "backend-user-projection")
    public void handle(
            @Payload String payload,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String eventTopic =
                eventTopicHeader != null
                        ? new String(eventTopicHeader, StandardCharsets.UTF_8)
                        : "";
        try {
            if (UPDATED_TOPIC.equals(eventTopic)) {
                UserUpdatedEvent event = objectMapper.readValue(payload, UserUpdatedEvent.class);
                projectionService.updateProjectedNickname(event.userId(), event.nickname());
            } else if (STATUS_CHANGED_TOPIC.equals(eventTopic)) {
                UserStatusChangedEvent event =
                        objectMapper.readValue(payload, UserStatusChangedEvent.class);
                projectionService.updateProjectedStatus(event.userId(), event.status());
            } else {
                UserCreatedEvent event = objectMapper.readValue(payload, UserCreatedEvent.class);
                projectionService.bootstrap(
                        event.userId(), event.username(), event.email(), event.nickname());
            }
        } catch (Exception exception) {
            log.error("user event 처리 실패. topic={}, payload={}", eventTopic, payload, exception);
            throw new IllegalStateException("user-events 처리 실패", exception);
        }
    }

    private record UserCreatedEvent(Long userId, String username, String email, String nickname) {}

    private record UserUpdatedEvent(Long userId, String nickname) {}

    private record UserStatusChangedEvent(Long userId, String status) {}
}
