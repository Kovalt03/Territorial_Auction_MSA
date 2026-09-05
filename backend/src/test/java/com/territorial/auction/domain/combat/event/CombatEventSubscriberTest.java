package com.territorial.auction.domain.combat.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.territorial.auction.domain.notification.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.global.client.MapTerritoryClient;
import com.territorial.auction.global.client.SeasonGameEventClient;
import com.territorial.auction.global.event.CombatEventReceiptService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class CombatEventSubscriberTest {

    @Mock private CombatEventReceiptService receiptService;
    @Mock private NotificationService notificationService;
    @Mock private MapTerritoryClient mapTerritoryClient;
    @Mock private SeasonGameEventClient seasonGameEventClient;
    @Mock private RedissonClient redissonClient;
    @Mock private RTopic siegeTopic;
    private CombatEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        subscriber =
                new CombatEventSubscriber(
                        objectMapper,
                        receiptService,
                        notificationService,
                        mapTerritoryClient,
                        seasonGameEventClient,
                        redissonClient);
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
        given(redissonClient.getTopic("siege.alert")).willReturn(siegeTopic);
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
        // 공성 알림은 Redis(siege.alert)로 발행 → realtime-service가 relay
        then(siegeTopic).should().publish(anyString());
    }

    @Test
    void takeoverEventRoutesToMapService() {
        subscriber.handleMap(
                "{\"siegeId\":100,\"territoryId\":10,\"newOwnerId\":1,\"formerOwnerId\":2,\"recoveredGp\":800}",
                bytes("combat.territory.takeover-requested"),
                bytes("event-2"));

        then(receiptService).should().processOnce(eq("map:event-2"), any());
        then(mapTerritoryClient).should().takeOver(10L, 1L, 2L);
    }

    @Test
    void victoryEventDelegatesSiegeWinToSeasonService() {
        subscriber.handleSeason(
                "{\"siegeId\":100,\"attackerId\":1}",
                bytes("combat.siege.victory"),
                bytes("event-3"));

        then(receiptService).should().processOnce(eq("season:event-3"), any());
        then(seasonGameEventClient).should().sendGameEvent(1L, "SIEGE_WIN");
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
