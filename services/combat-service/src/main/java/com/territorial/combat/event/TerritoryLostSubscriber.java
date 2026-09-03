package com.territorial.combat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class TerritoryLostSubscriber {

    public static final String TOPIC = "territory-events";
    public static final String TERRITORY_LOST = "territory.lost";

    private final ObjectMapper objectMapper;
    private final TerritoryLossService territoryLossService;

    @KafkaListener(topics = TOPIC, groupId = "combat-territory-loss")
    public void handle(
            @Payload String payload,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String eventTopic = readEventTopic(eventTopicHeader);
        if (!TERRITORY_LOST.equals(eventTopic)) {
            log.warn("지원하지 않는 영토 이벤트 무시. eventTopic={}", eventTopic);
            return;
        }
        try {
            TerritoryLostEvent event = objectMapper.readValue(payload, TerritoryLostEvent.class);
            territoryLossService.handle(event.territoryId(), event.formerOwnerId());
        } catch (Exception exception) {
            log.error("영토 상실 이벤트 처리 실패. eventTopic={}", eventTopic, exception);
            throw new IllegalStateException("territory-events 처리 실패", exception);
        }
    }

    private String readEventTopic(byte[] header) {
        return header == null ? "" : new String(header, StandardCharsets.UTF_8);
    }

    private record TerritoryLostEvent(Long territoryId, Long formerOwnerId) {}
}
