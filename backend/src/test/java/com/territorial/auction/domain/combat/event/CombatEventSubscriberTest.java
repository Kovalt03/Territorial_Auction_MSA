package com.territorial.auction.domain.combat.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.territorial.auction.domain.map.service.TerritoryService;
import com.territorial.auction.domain.notification.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.global.event.CombatEventReceiptService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CombatEventSubscriberTest {

    @Mock private CombatEventReceiptService receiptService;
    @Mock private NotificationService notificationService;
    @Mock private TerritoryService territoryService;
    @Mock private SeasonRepository seasonRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SimpMessagingTemplate messagingTemplate;
    private CombatEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        subscriber =
                new CombatEventSubscriber(
                        objectMapper,
                        receiptService,
                        notificationService,
                        territoryService,
                        seasonRepository,
                        eventPublisher,
                        messagingTemplate);
        org.mockito.Mockito.lenient()
                .doAnswer(
                        invocation -> {
                            invocation.<Runnable>getArgument(1).run();
                            return null;
                        })
                .when(receiptService)
                .processOnce(any(), any());
    }

    @Test
    void declaredEventNotifiesDefenderAndUsesEventIdReceipt() {
        String payload =
                """
                {"siegeId":100,"territoryId":10,"coordX":4,"coordY":5,"attackZone":3,
                 "attackerId":1,"attackerNickname":"공격자","defenderId":2,
                 "defenderNickname":"방어자","resolveAt":"2026-09-02T13:00:00"}
                """;

        subscriber.handleNotification(payload, bytes("combat.siege.declared"), bytes("event-1"));

        then(receiptService).should().processOnce(eq("notification:event-1"), any());
        then(notificationService)
                .should()
                .sendNotification(eq(2L), eq(NotificationType.SIEGE_ALERT), any());
        then(messagingTemplate)
                .should()
                .convertAndSend(eq("/sub/user/2/siege-alert"), any(Object.class));
    }

    @Test
    void takeoverEventRoutesToMapService() {
        subscriber.handleMap(
                "{\"siegeId\":100,\"territoryId\":10,\"newOwnerId\":1,\"formerOwnerId\":2,\"recoveredGp\":800}",
                bytes("combat.territory.takeover-requested"),
                bytes("event-2"));

        then(receiptService).should().processOnce(eq("map:event-2"), any());
        then(territoryService).should().takeOverFromSiege(10L, 1L, 2L);
    }

    @Test
    void victoryEventPublishesCurrentSeasonEvent() {
        Season season =
                Season.builder()
                        .seasonNumber(1)
                        .startedAt(LocalDateTime.now().minusDays(1))
                        .endedAt(LocalDateTime.now().plusDays(1))
                        .build();
        ReflectionTestUtils.setField(season, "id", 9L);
        given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                .willReturn(Optional.of(season));

        subscriber.handleSeason(
                "{\"siegeId\":100,\"attackerId\":1}",
                bytes("combat.siege.victory"),
                bytes("event-3"));

        then(eventPublisher).should().publishEvent(new SiegeVictoryEvent(1L, 9L));
    }

    @Test
    void unrelatedTopicIsIgnoredByMapGroup() {
        subscriber.handleMap("{}", bytes("combat.siege.declared"), bytes("event-4"));

        then(receiptService).should(never()).processOnce(eq("map:event-4"), any());
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
