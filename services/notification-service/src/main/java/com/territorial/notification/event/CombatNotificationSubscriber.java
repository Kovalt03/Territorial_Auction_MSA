package com.territorial.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.notification.domain.notification.entity.NotificationType;
import com.territorial.notification.domain.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 공성·섬 확장 알림 구독기. combat-service의 combat.siege.declared/resolved·combat.island.expanded(Kafka
 * `combat-events`)를 받아 사용자 알림을 저장한다. 과거 모놀리식 브리지가 담당하던 알림 생성을 대체한다.
 *
 * <p>알림 저장은 비멱등이라 receipt로 중복을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CombatNotificationSubscriber {

    private static final String DECLARED = "combat.siege.declared";
    private static final String RESOLVED = "combat.siege.resolved";
    private static final String ISLAND_EXPANDED = "combat.island.expanded";

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final CombatEventReceiptService receiptService;

    @KafkaListener(topics = "combat-events", groupId = "notification-combat")
    public void handle(
            @Payload String json,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader,
            @Header(name = "event-id", required = false) byte[] eventIdHeader) {
        String topic = header(eventTopicHeader);
        String eventId = header(eventIdHeader);
        try {
            switch (topic) {
                case DECLARED -> handleDeclared(json, eventId);
                case RESOLVED -> handleResolved(json, eventId);
                case ISLAND_EXPANDED -> handleIslandExpanded(json, eventId);
                default -> {
                    /* 그 외 combat 이벤트는 알림 관심 밖 */
                }
            }
        } catch (Exception ex) {
            log.error("[CombatNotification] 처리 실패: topic={}, payload={}", topic, json, ex);
            throw new IllegalStateException("combat 알림 처리 실패", ex);
        }
    }

    private void handleDeclared(String json, String eventId) throws Exception {
        SiegeDeclared e = objectMapper.readValue(json, SiegeDeclared.class);
        receiptService.processOnce(
                receiptKey(DECLARED, eventId, e.siegeId()),
                () ->
                        notificationService.persist(
                                e.defenderId(),
                                NotificationType.SIEGE_ALERT,
                                e.attackerNickname()
                                        + "님이 ("
                                        + e.coordX()
                                        + ", "
                                        + e.coordY()
                                        + ") 영토를 공격했습니다. (Zone "
                                        + e.attackZone()
                                        + ")"));
    }

    private void handleResolved(String json, String eventId) throws Exception {
        SiegeResolved e = objectMapper.readValue(json, SiegeResolved.class);
        String coord = "(" + e.coordX() + ", " + e.coordY() + ")";
        receiptService.processOnce(
                receiptKey(RESOLVED, eventId, e.siegeId()),
                () -> {
                    notificationService.persist(
                            e.defenderId(),
                            NotificationType.SIEGE_RESULT,
                            coord + " 영토 공성 정산 — 방어 " + (e.isAttackerWin() ? "실패" : "성공") + ".");
                    notificationService.persist(
                            e.attackerId(),
                            NotificationType.SIEGE_RESULT,
                            coord + " 영토 공성 정산 — " + (e.isAttackerWin() ? "승리" : "패배") + ".");
                });
    }

    private void handleIslandExpanded(String json, String eventId) throws Exception {
        IslandExpanded e = objectMapper.readValue(json, IslandExpanded.class);
        receiptService.processOnce(
                receiptKey(ISLAND_EXPANDED, eventId, e.userId()),
                () ->
                        notificationService.persist(
                                e.userId(),
                                NotificationType.ISLAND_EXPANDED,
                                "섬이 넓어지면서 배치 규칙을 벗어난 건물 "
                                        + e.storedBuildingCount()
                                        + "개가 보관함으로 이동했습니다."));
    }

    private String receiptKey(String topic, String eventId, Object aggregateId) {
        return eventId.isBlank() ? topic + ":" + aggregateId : eventId;
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

    private record IslandExpanded(Long userId, int storedBuildingCount) {}
}
