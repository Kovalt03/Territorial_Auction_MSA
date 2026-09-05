package com.territorial.map.domain.map.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.map.domain.map.service.TerritoryService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 공성 인계 구독기. combat-service의 combat.territory.takeover-requested(Kafka `combat-events`)를 받아 영토 소유권을
 * 인계한다. map이 영토를 소유하므로 로컬에서 직접 처리한다(과거 모놀리식 브리지가 map /internal/takeover를 호출하던 것을 대체).
 *
 * <p>takeOverFromSiege는 멱등(현재 소유자==새 소유자면 no-op, formerOwner 불일치면 무시)이라 별도 receipt 없이 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CombatTakeoverSubscriber {

    private static final String TAKEOVER = "combat.territory.takeover-requested";

    private final ObjectMapper objectMapper;
    private final TerritoryService territoryService;

    @KafkaListener(topics = "combat-events", groupId = "map-combat-takeover")
    public void handle(
            @Payload String json,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String topic =
                eventTopicHeader != null
                        ? new String(eventTopicHeader, StandardCharsets.UTF_8)
                        : "";
        if (!TAKEOVER.equals(topic)) {
            return;
        }
        try {
            TerritoryTakeoverRequested event =
                    objectMapper.readValue(json, TerritoryTakeoverRequested.class);
            territoryService.takeOverFromSiege(
                    event.territoryId(), event.newOwnerId(), event.formerOwnerId());
        } catch (Exception ex) {
            log.error("[CombatTakeover] 처리 실패: payload={}", json, ex);
            throw new IllegalStateException("combat.territory.takeover-requested 처리 실패", ex);
        }
    }

    private record TerritoryTakeoverRequested(
            Long siegeId, Long territoryId, Long newOwnerId, Long formerOwnerId, int recoveredGp) {}
}
