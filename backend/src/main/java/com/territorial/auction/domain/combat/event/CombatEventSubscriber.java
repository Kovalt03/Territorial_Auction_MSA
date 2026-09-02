package com.territorial.auction.domain.combat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.map.service.TerritoryService;
import com.territorial.auction.domain.military.dto.SiegeAlert;
import com.territorial.auction.domain.military.event.SiegeVictoryEvent;
import com.territorial.auction.domain.notification.entity.NotificationLog.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.global.event.CombatEventReceiptService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class CombatEventSubscriber {

    public static final String TOPIC = "combat-events";
    private static final String DECLARED = "combat.siege.declared";
    private static final String RESOLVED = "combat.siege.resolved";
    private static final String TAKEOVER = "combat.territory.takeover-requested";
    private static final String VICTORY = "combat.siege.victory";
    private static final String ISLAND_EXPANDED = "combat.island.expanded";

    private final ObjectMapper objectMapper;
    private final CombatEventReceiptService receiptService;
    private final NotificationService notificationService;
    private final TerritoryService territoryService;
    private final SeasonRepository seasonRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = TOPIC, groupId = "backend-combat-notification")
    public void handleNotification(
            @Payload String payload,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader,
            @Header(name = "event-id", required = false) byte[] eventIdHeader) {
        String eventTopic = readEventTopic(eventTopicHeader);
        String eventId = readHeader(eventIdHeader);
        try {
            switch (eventTopic) {
                case DECLARED -> handleDeclared(payload, eventId);
                case RESOLVED -> handleResolved(payload, eventId);
                case ISLAND_EXPANDED -> handleIslandExpanded(payload, eventId);
                default -> log.debug("notification group이 이벤트를 건너뜀. eventTopic={}", eventTopic);
            }
        } catch (Exception exception) {
            throw processingFailure(eventTopic, exception);
        }
    }

    @KafkaListener(topics = TOPIC, groupId = "backend-combat-map")
    public void handleMap(
            @Payload String payload,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader,
            @Header(name = "event-id", required = false) byte[] eventIdHeader) {
        String eventTopic = readEventTopic(eventTopicHeader);
        if (!TAKEOVER.equals(eventTopic)) {
            return;
        }
        try {
            TerritoryTakeoverRequested event =
                    objectMapper.readValue(payload, TerritoryTakeoverRequested.class);
            receiptService.processOnce(
                    receiptKey("map", eventIdHeader, eventTopic, event.siegeId()),
                    () ->
                            territoryService.takeOverFromSiege(
                                    event.territoryId(),
                                    event.newOwnerId(),
                                    event.formerOwnerId()));
        } catch (Exception exception) {
            throw processingFailure(eventTopic, exception);
        }
    }

    @KafkaListener(topics = TOPIC, groupId = "backend-combat-season")
    public void handleSeason(
            @Payload String payload,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader,
            @Header(name = "event-id", required = false) byte[] eventIdHeader) {
        String eventTopic = readEventTopic(eventTopicHeader);
        if (!VICTORY.equals(eventTopic)) {
            return;
        }
        try {
            SiegeVictory event = objectMapper.readValue(payload, SiegeVictory.class);
            receiptService.processOnce(
                    receiptKey("season", eventIdHeader, eventTopic, event.siegeId()),
                    () ->
                            seasonRepository
                                    .findActiveSeason(LocalDateTime.now())
                                    .ifPresent(
                                            season ->
                                                    eventPublisher.publishEvent(
                                                            new SiegeVictoryEvent(
                                                                    event.attackerId(),
                                                                    season.getId()))));
        } catch (Exception exception) {
            throw processingFailure(eventTopic, exception);
        }
    }

    private void handleDeclared(String payload, String eventId) throws Exception {
        SiegeDeclared event = objectMapper.readValue(payload, SiegeDeclared.class);
        receiptService.processOnce(
                receiptKey("notification", eventId, DECLARED, event.siegeId()),
                () -> {
                    notificationService.sendNotification(
                            event.defenderId(),
                            NotificationType.SIEGE_ALERT,
                            event.attackerNickname()
                                    + "님이 ("
                                    + event.coordX()
                                    + ", "
                                    + event.coordY()
                                    + ") 영토를 공격했습니다. (Zone "
                                    + event.attackZone()
                                    + ")");
                    sendAfterCommit(
                            "/sub/user/" + event.defenderId() + "/siege-alert",
                            new SiegeAlert(
                                    event.siegeId(),
                                    "DECLARED",
                                    event.territoryId(),
                                    event.coordX(),
                                    event.coordY(),
                                    event.attackZone(),
                                    event.attackerId(),
                                    event.attackerNickname(),
                                    event.defenderId(),
                                    event.defenderNickname(),
                                    event.resolveAt(),
                                    null,
                                    null));
                });
    }

    private void handleResolved(String payload, String eventId) throws Exception {
        SiegeResolved event = objectMapper.readValue(payload, SiegeResolved.class);
        receiptService.processOnce(
                receiptKey("notification", eventId, RESOLVED, event.siegeId()),
                () -> {
                    String coord = "(" + event.coordX() + ", " + event.coordY() + ")";
                    notificationService.sendNotification(
                            event.defenderId(),
                            NotificationType.SIEGE_RESULT,
                            coord
                                    + " 영토 공성 정산 — 방어 "
                                    + (event.isAttackerWin() ? "실패" : "성공")
                                    + ".");
                    notificationService.sendNotification(
                            event.attackerId(),
                            NotificationType.SIEGE_RESULT,
                            coord + " 영토 공성 정산 — " + (event.isAttackerWin() ? "승리" : "패배") + ".");
                    SiegeAlert alert =
                            new SiegeAlert(
                                    event.siegeId(),
                                    "RESOLVED",
                                    event.territoryId(),
                                    event.coordX(),
                                    event.coordY(),
                                    event.attackZone(),
                                    event.attackerId(),
                                    event.attackerNickname(),
                                    event.defenderId(),
                                    event.defenderNickname(),
                                    event.resolvedAt(),
                                    event.isAttackerWin(),
                                    event.resultType());
                    sendAfterCommit("/sub/user/" + event.attackerId() + "/siege-alert", alert);
                    sendAfterCommit("/sub/user/" + event.defenderId() + "/siege-alert", alert);
                });
    }

    private void handleIslandExpanded(String payload, String eventId) throws Exception {
        IslandExpanded event = objectMapper.readValue(payload, IslandExpanded.class);
        receiptService.processOnce(
                receiptKey("notification", eventId, ISLAND_EXPANDED, event.userId()),
                () ->
                        notificationService.sendNotification(
                                event.userId(),
                                NotificationType.ISLAND_EXPANDED,
                                "섬이 넓어지면서 배치 규칙을 벗어난 건물 "
                                        + event.storedBuildingCount()
                                        + "개가 보관함으로 이동했습니다."));
    }

    private void sendAfterCommit(String destination, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            messagingTemplate.convertAndSend(destination, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(destination, payload);
                    }
                });
    }

    private String receiptKey(
            String group, byte[] eventIdHeader, String eventTopic, Object aggregateId) {
        return receiptKey(group, readHeader(eventIdHeader), eventTopic, aggregateId);
    }

    private String receiptKey(String group, String eventId, String eventTopic, Object aggregateId) {
        String identity = eventId.isBlank() ? eventTopic + ":" + aggregateId : eventId;
        return group + ":" + identity;
    }

    private String readEventTopic(byte[] header) {
        return readHeader(header);
    }

    private String readHeader(byte[] header) {
        return header == null ? "" : new String(header, StandardCharsets.UTF_8);
    }

    private IllegalStateException processingFailure(String eventTopic, Exception exception) {
        log.error("combat 이벤트 처리 실패. eventTopic={}", eventTopic, exception);
        return new IllegalStateException("combat-events 처리 실패", exception);
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

    private record TerritoryTakeoverRequested(
            Long siegeId, Long territoryId, Long newOwnerId, Long formerOwnerId, int recoveredGp) {}

    private record SiegeVictory(Long siegeId, Long attackerId) {}

    private record IslandExpanded(Long userId, int storedBuildingCount) {}
}
