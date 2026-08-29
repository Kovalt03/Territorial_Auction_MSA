package com.territorial.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class UserOutboxPublisherTest {

    @InjectMocks private UserOutboxPublisher userOutboxPublisher;
    @Mock private UserOutboxEventRepository outboxEventRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOperations;

    @Test
    void successfulStreamAppendMarksOutboxPublished() {
        UserOutboxEvent event = new UserOutboxEvent("user.created", "{\"userId\":1}");
        given(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(redisTemplate.opsForStream()).willReturn(streamOperations);
        given(streamOperations.add(any())).willReturn(RecordId.of("1-0"));

        userOutboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void failedStreamAppendLeavesOutboxPending() {
        UserOutboxEvent event = new UserOutboxEvent("user.created", "{\"userId\":1}");
        given(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(redisTemplate.opsForStream()).willReturn(streamOperations);
        given(streamOperations.add(any())).willThrow(new IllegalStateException("redis down"));

        userOutboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNull();
    }
}
