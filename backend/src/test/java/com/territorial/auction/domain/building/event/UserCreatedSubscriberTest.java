package com.territorial.auction.domain.building.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.building.service.UserBootstrapService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserCreatedSubscriberTest {

    @Mock private UserBootstrapService userBootstrapService;
    private UserCreatedSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new UserCreatedSubscriber(new ObjectMapper(), userBootstrapService);
    }

    private byte[] topic(String t) {
        return t.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void createdEventBootstrapsProjection() {
        subscriber.handle(
                "{\"userId\":7,\"username\":\"user7\",\"email\":\"u7@example.com\",\"nickname\":\"유저7\"}",
                topic("user.created"));

        verify(userBootstrapService).bootstrap(7L, "user7", "u7@example.com", "유저7");
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
    void poisonPayloadThrowsForRetry() {
        assertThatThrownBy(() -> subscriber.handle("not-json", topic("user.created")))
                .isInstanceOf(IllegalStateException.class);
    }
}
