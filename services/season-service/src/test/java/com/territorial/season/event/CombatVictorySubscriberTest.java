package com.territorial.season.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.season.internal.SeasonInternalService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CombatVictorySubscriberTest {

    @Mock private SeasonInternalService seasonInternalService;
    @Mock private CombatEventReceiptService receiptService;
    private CombatVictorySubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber =
                new CombatVictorySubscriber(
                        new ObjectMapper(), seasonInternalService, receiptService);
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
    void victoryGrantsSiegeWinWithEventIdReceipt() {
        subscriber.handle(
                "{\"siegeId\":100,\"attackerId\":1}",
                bytes("combat.siege.victory"),
                bytes("event-1"));

        then(receiptService).should().processOnce(eq("event-1"), any());
        then(seasonInternalService).should().handleGameEvent(1L, "SIEGE_WIN");
    }

    @Test
    void unrelatedTopicIsIgnored() {
        subscriber.handle("{}", bytes("combat.siege.declared"), bytes("event-2"));

        then(receiptService).should(never()).processOnce(any(), any());
        then(seasonInternalService).shouldHaveNoInteractions();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
