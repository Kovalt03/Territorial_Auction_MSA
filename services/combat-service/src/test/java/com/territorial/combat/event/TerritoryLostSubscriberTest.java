package com.territorial.combat.event;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerritoryLostSubscriberTest {

    @Mock private TerritoryLossService territoryLossService;
    private TerritoryLostSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new TerritoryLostSubscriber(new ObjectMapper(), territoryLossService);
    }

    @Test
    void routesTerritoryLostPayload() {
        subscriber.handle("{\"territoryId\":10,\"formerOwnerId\":2}", bytes("territory.lost"));

        then(territoryLossService).should().handle(10L, 2L);
    }

    @Test
    void ignoresUnknownEventTopic() {
        subscriber.handle("{}", bytes("territory.unknown"));

        then(territoryLossService).should(never()).handle(10L, 2L);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
