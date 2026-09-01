package com.territorial.social.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.social.domain.user.entity.UserDisplay;
import com.territorial.social.domain.user.repository.UserDisplayRepository;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** user-service 이벤트(Kafka user-events)를 구독해 표시용 닉네임 프로젝션을 갱신. userId 기준 멱등 upsert. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDisplaySubscriber {

    private static final String CREATED = "user.created";
    private static final String UPDATED = "user.updated";

    private final ObjectMapper objectMapper;
    private final UserDisplayRepository userDisplayRepository;

    @KafkaListener(topics = "user-events", groupId = "social-user-projection")
    @Transactional
    public void handle(
            @Payload String json,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String topic =
                eventTopicHeader != null
                        ? new String(eventTopicHeader, StandardCharsets.UTF_8)
                        : "";
        try {
            if (CREATED.equals(topic) || UPDATED.equals(topic)) {
                UserEvent e = objectMapper.readValue(json, UserEvent.class);
                upsert(e.userId(), e.nickname());
            }
            // status-changed 등은 표시에 불필요 → 무시
        } catch (Exception e) {
            log.error("[UserDisplaySubscriber] 처리 실패: topic={}, payload={}", topic, json, e);
            throw new IllegalStateException("user-events 처리 실패", e);
        }
    }

    private void upsert(Long userId, String nickname) {
        if (nickname == null) {
            return;
        }
        userDisplayRepository
                .findById(userId)
                .ifPresentOrElse(
                        d -> d.updateNickname(nickname),
                        () -> userDisplayRepository.save(new UserDisplay(userId, nickname)));
    }

    private record UserEvent(Long userId, String nickname) {}
}
