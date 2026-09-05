package com.territorial.season.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.season.internal.SeasonInternalService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 공성 승리 구독기. combat-service의 combat.siege.victory(Kafka `combat-events`)를 받아 공격자에게 SIEGE_WIN 게임
 * 이벤트(XP·미션)를 적립한다. 과거 모놀리식 브리지가 season /internal game-event를 호출하던 것을 대체.
 *
 * <p>XP·미션 적립은 비멱등이라 receipt로 중복 처리를 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CombatVictorySubscriber {

    private static final String VICTORY = "combat.siege.victory";

    private final ObjectMapper objectMapper;
    private final SeasonInternalService seasonInternalService;
    private final CombatEventReceiptService receiptService;

    @KafkaListener(topics = "combat-events", groupId = "season-combat-victory")
    public void handle(
            @Payload String json,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader,
            @Header(name = "event-id", required = false) byte[] eventIdHeader) {
        String topic = header(eventTopicHeader);
        if (!VICTORY.equals(topic)) {
            return;
        }
        try {
            SiegeVictory event = objectMapper.readValue(json, SiegeVictory.class);
            String receiptKey = receiptKey(header(eventIdHeader), event.siegeId());
            receiptService.processOnce(
                    receiptKey,
                    () -> seasonInternalService.handleGameEvent(event.attackerId(), "SIEGE_WIN"));
        } catch (Exception ex) {
            log.error("[CombatVictory] 처리 실패: payload={}", json, ex);
            throw new IllegalStateException("combat.siege.victory 처리 실패", ex);
        }
    }

    private String receiptKey(String eventId, Long siegeId) {
        return eventId.isBlank() ? VICTORY + ":" + siegeId : eventId;
    }

    private String header(byte[] header) {
        return header == null ? "" : new String(header, StandardCharsets.UTF_8);
    }

    private record SiegeVictory(Long siegeId, Long attackerId) {}
}
