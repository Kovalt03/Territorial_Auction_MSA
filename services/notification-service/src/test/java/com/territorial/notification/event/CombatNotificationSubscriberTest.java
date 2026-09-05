package com.territorial.notification.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.territorial.notification.domain.notification.entity.NotificationType;
import com.territorial.notification.domain.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CombatNotificationSubscriberTest {

    @Mock private NotificationService notificationService;
    @Mock private CombatEventReceiptService receiptService;
    private CombatNotificationSubscriber subscriber;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        subscriber =
                new CombatNotificationSubscriber(objectMapper, notificationService, receiptService);
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
    void declaredNotifiesDefenderWithEventIdReceipt() {
        String payload =
                """
                {"siegeId":100,"territoryId":10,"coordX":4,"coordY":5,"attackZone":3,
                 "attackerId":1,"attackerNickname":"공격자","defenderId":2,
                 "defenderNickname":"방어자","resolveAt":"2026-09-02T13:00:00"}
                """;

        subscriber.handle(payload, bytes("combat.siege.declared"), bytes("event-1"));

        then(receiptService).should().processOnce(eq("event-1"), any());
        then(notificationService)
                .should()
                .persist(eq(2L), eq(NotificationType.SIEGE_ALERT), contains("공격했습니다"));
    }

    @Test
    void resolvedNotifiesBothSides() {
        String payload =
                """
                {"siegeId":100,"territoryId":10,"coordX":4,"coordY":5,"attackZone":3,
                 "attackerId":1,"attackerNickname":"공격자","defenderId":2,"defenderNickname":"방어자",
                 "isAttackerWin":true,"resultType":"OCCUPIED","attackerUnitsLost":1,
                 "defenderUnitsLost":2,"lootedGp":50,"resolvedAt":"2026-09-02T14:00:00"}
                """;

        subscriber.handle(payload, bytes("combat.siege.resolved"), bytes("event-2"));

        then(notificationService)
                .should()
                .persist(eq(2L), eq(NotificationType.SIEGE_RESULT), contains("방어 실패"));
        then(notificationService)
                .should()
                .persist(eq(1L), eq(NotificationType.SIEGE_RESULT), contains("승리"));
    }

    @Test
    void unrelatedTopicIsIgnored() {
        subscriber.handle("{}", bytes("combat.siege.victory"), bytes("event-3"));

        then(receiptService).should(never()).processOnce(any(), any());
        then(notificationService).shouldHaveNoInteractions();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
