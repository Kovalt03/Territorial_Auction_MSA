package com.territorial.auction.domain.building.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.building.service.UserBootstrapService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class UserCreatedSubscriberTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOperations;
    @Mock private UserBootstrapService userBootstrapService;
    private UserCreatedSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber =
                new UserCreatedSubscriber(redisTemplate, new ObjectMapper(), userBootstrapService);
        given(redisTemplate.opsForStream()).willReturn(streamOperations);
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonPendingRecordDoesNotBlockNewSignup() {
        MapRecord<String, Object, Object> poison = record("1-0", "not-json");
        MapRecord<String, Object, Object> signup =
                record(
                        "2-0",
                        "{\"userId\":7,\"username\":\"user7\",\"email\":\"u7@example.com\",\"nickname\":\"유저7\"}");
        given(
                        streamOperations.read(
                                any(Consumer.class),
                                any(StreamReadOptions.class),
                                any(StreamOffset[].class)))
                .willReturn(List.of(poison), List.of(signup));

        subscriber.poll();

        verify(userBootstrapService).bootstrap(7L, "user7", "u7@example.com", "유저7");
        verify(streamOperations)
                .acknowledge(
                        eq("stream:user-events"), eq("backend-user-bootstrap"), eq(signup.getId()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void existingConsumerGroupStillPollsPendingAndNewRecords() {
        given(
                        streamOperations.createGroup(
                                eq("stream:user-events"), any(), eq("backend-user-bootstrap")))
                .willThrow(
                        new RedisSystemException(
                                "Error in execution",
                                new IllegalStateException(
                                        "BUSYGROUP Consumer Group already exists")));
        given(
                        streamOperations.read(
                                any(Consumer.class),
                                any(StreamReadOptions.class),
                                any(StreamOffset[].class)))
                .willReturn(List.of());

        subscriber.poll();

        verify(streamOperations, times(2))
                .read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset[].class));
    }

    private MapRecord<String, Object, Object> record(String id, String payload) {
        return MapRecord.create(
                        "stream:user-events",
                        Map.<Object, Object>of("topic", "user.created", "payload", payload))
                .withId(RecordId.of(id));
    }
}
