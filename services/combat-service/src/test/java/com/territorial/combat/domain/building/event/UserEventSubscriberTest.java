package com.territorial.combat.domain.building.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.combat.domain.building.service.UserBootstrapService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserEventSubscriberTest {

    @Mock private UserBootstrapService userBootstrapService;
    private UserEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new UserEventSubscriber(new ObjectMapper(), userBootstrapService);
    }

    @Test
    void createdEventBootstrapsCombatState() {
        subscriber.handle(
                "{\"userId\":7,\"username\":\"user7\",\"email\":\"u7@example.com\",\"nickname\":\"유저7\"}",
                topic("user.created"));

        verify(userBootstrapService).bootstrap(7L, "유저7");
    }

    @Test
    void updatedEventUpdatesNickname() {
        subscriber.handle("{\"userId\":7,\"nickname\":\"새닉\"}", topic("user.updated"));

        verify(userBootstrapService).updateProjectedNickname(7L, "새닉");
    }

    @Test
    void statusChangedEventUpdatesStatus() {
        subscriber.handle("{\"userId\":7,\"status\":\"WITHDRAWN\"}", topic("user.status-changed"));

        verify(userBootstrapService).updateProjectedStatus(7L, "WITHDRAWN");
    }

    @Test
    void unknownEventIsIgnored() {
        subscriber.handle("{}", topic("user.unknown"));

        verifyNoInteractions(userBootstrapService);
    }

    @Test
    void poisonPayloadThrowsForRetry() {
        assertThatThrownBy(() -> subscriber.handle("not-json", topic("user.created")))
                .isInstanceOf(IllegalStateException.class);
    }

    private byte[] topic(String eventTopic) {
        return eventTopic.getBytes(StandardCharsets.UTF_8);
    }
}
