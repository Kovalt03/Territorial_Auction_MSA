package com.territorial.auction.domain.building.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.building.service.UserBootstrapService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** user-service 이벤트(Kafka `user-events`)를 소비해 모놀리식 User 프로젝션에 반영. 핸들러는 userId 기준 멱등. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedSubscriber {

    private static final String UPDATED_TOPIC = "user.updated";
    private static final String STATUS_CHANGED_TOPIC = "user.status-changed";

    private final ObjectMapper objectMapper;
    private final UserBootstrapService userBootstrapService;

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
                userBootstrapService.updateProjectedNickname(event.userId(), event.nickname());
            } else if (STATUS_CHANGED_TOPIC.equals(eventTopic)) {
                UserStatusChangedEvent event =
                        objectMapper.readValue(payload, UserStatusChangedEvent.class);
                userBootstrapService.updateProjectedStatus(event.userId(), event.status());
            } else {
                UserCreatedEvent event = objectMapper.readValue(payload, UserCreatedEvent.class);
                userBootstrapService.bootstrap(
                        event.userId(), event.username(), event.email(), event.nickname());
            }
        } catch (Exception e) {
            // 재던져 Spring Kafka 기본 에러 핸들러의 재시도(전이 오류) → 최종 스킵에 위임.
            log.error("[UserEventListener] 처리 실패: topic={}, payload={}", eventTopic, payload, e);
            throw new IllegalStateException("user-events 처리 실패", e);
        }
    }

    private record UserCreatedEvent(Long userId, String username, String email, String nickname) {}

    private record UserUpdatedEvent(Long userId, String nickname) {}

    private record UserStatusChangedEvent(Long userId, String status) {}
}
