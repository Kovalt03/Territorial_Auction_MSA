package com.territorial.realtime.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 허브. combat-service의 combat.siege.declared/resolved(Kafka `combat-events`)를 구독해
 * /sub/user/{userId}/siege-alert로 WS push한다. 과거 모놀리식 브리지가 담당하던 WS 경로를 대체한다.
 *
 * <p>WS push는 표시용이라 중복 전송이 무해하므로 별도 receipt를 두지 않는다(알림 저장은 combat→notification 경로가 담당).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiegeEventSubscriber {

    private static final String DECLARED = "combat.siege.declared";
    private static final String RESOLVED = "combat.siege.resolved";

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "combat-events", groupId = "realtime-combat-siege")
    public void handle(
            @Payload String json,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String topic = header(eventTopicHeader);
        try {
            if (DECLARED.equals(topic)) {
                handleDeclared(objectMapper.readValue(json, SiegeDeclared.class));
            } else if (RESOLVED.equals(topic)) {
                handleResolved(objectMapper.readValue(json, SiegeResolved.class));
            }
        } catch (Exception ex) {
            log.error("[SiegeEvent] 처리 실패: topic={}, payload={}", topic, json, ex);
            throw new IllegalStateException("combat 공성 이벤트 WS 처리 실패", ex);
        }
    }

    private void handleDeclared(SiegeDeclared e) {
        SiegeAlert alert =
                new SiegeAlert(
                        e.siegeId(),
                        "DECLARED",
                        e.territoryId(),
                        e.coordX(),
                        e.coordY(),
                        e.attackZone(),
                        e.attackerId(),
                        e.attackerNickname(),
                        e.defenderId(),
                        e.defenderNickname(),
                        e.resolveAt(),
                        null,
                        null);
        messagingTemplate.convertAndSend("/sub/user/" + e.defenderId() + "/siege-alert", alert);
    }

    private void handleResolved(SiegeResolved e) {
        SiegeAlert alert =
                new SiegeAlert(
                        e.siegeId(),
                        "RESOLVED",
                        e.territoryId(),
                        e.coordX(),
                        e.coordY(),
                        e.attackZone(),
                        e.attackerId(),
                        e.attackerNickname(),
                        e.defenderId(),
                        e.defenderNickname(),
                        e.resolvedAt(),
                        e.isAttackerWin(),
                        e.resultType());
        messagingTemplate.convertAndSend("/sub/user/" + e.attackerId() + "/siege-alert", alert);
        messagingTemplate.convertAndSend("/sub/user/" + e.defenderId() + "/siege-alert", alert);
    }

    private String header(byte[] header) {
        return header == null ? "" : new String(header, StandardCharsets.UTF_8);
    }

    private record SiegeDeclared(
            Long siegeId,
            Long territoryId,
            int coordX,
            int coordY,
            int attackZone,
            Long attackerId,
            String attackerNickname,
            Long defenderId,
            String defenderNickname,
            LocalDateTime resolveAt) {}

    private record SiegeResolved(
            Long siegeId,
            Long territoryId,
            int coordX,
            int coordY,
            int attackZone,
            Long attackerId,
            String attackerNickname,
            Long defenderId,
            String defenderNickname,
            boolean isAttackerWin,
            String resultType,
            int attackerUnitsLost,
            int defenderUnitsLost,
            int lootedGp,
            LocalDateTime resolvedAt) {}
}
