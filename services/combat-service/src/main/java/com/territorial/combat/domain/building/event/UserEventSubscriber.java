package com.territorial.combat.domain.building.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.combat.domain.building.service.UserBootstrapService;
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
public class UserEventSubscriber {

    private static final String CREATED_TOPIC = "user.created";
    private static final String UPDATED_TOPIC = "user.updated";
    private static final String STATUS_CHANGED_TOPIC = "user.status-changed";

    private final ObjectMapper objectMapper;
    private final UserBootstrapService userBootstrapService;

    @KafkaListener(
            id = "combatUserEventListener",
            topics = "user-events",
            groupId = "combat-user-projection",
            autoStartup = "false")
    public void handle(
            @Payload String payload,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String eventTopic = readEventTopic(eventTopicHeader);
        try {
            handleEvent(eventTopic, payload);
        } catch (Exception e) {
            log.error("사용자 이벤트 처리 실패. eventTopic={}", eventTopic, e);
            throw new IllegalStateException("user-events 처리 실패", e);
        }
    }

    private void handleEvent(String eventTopic, String payload) throws Exception {
        switch (eventTopic) {
            case CREATED_TOPIC -> {
                UserCreatedEvent event = objectMapper.readValue(payload, UserCreatedEvent.class);
                userBootstrapService.bootstrap(event.userId(), event.nickname());
            }
            case UPDATED_TOPIC -> {
                UserUpdatedEvent event = objectMapper.readValue(payload, UserUpdatedEvent.class);
                userBootstrapService.updateProjectedNickname(event.userId(), event.nickname());
            }
            case STATUS_CHANGED_TOPIC -> {
                UserStatusChangedEvent event =
                        objectMapper.readValue(payload, UserStatusChangedEvent.class);
                userBootstrapService.updateProjectedStatus(event.userId(), event.status());
            }
            default -> log.warn("지원하지 않는 사용자 이벤트 무시. eventTopic={}", eventTopic);
        }
    }

    private String readEventTopic(byte[] eventTopicHeader) {
        return eventTopicHeader == null ? "" : new String(eventTopicHeader, StandardCharsets.UTF_8);
    }

    private record UserCreatedEvent(Long userId, String username, String email, String nickname) {}

    private record UserUpdatedEvent(Long userId, String nickname) {}

    private record UserStatusChangedEvent(Long userId, String status) {}
}
