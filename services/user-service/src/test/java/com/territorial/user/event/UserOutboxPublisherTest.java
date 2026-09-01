package com.territorial.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class UserOutboxPublisherTest {

    @InjectMocks private UserOutboxPublisher userOutboxPublisher;
    @Mock private UserOutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void successfulSendMarksOutboxPublished() {
        UserOutboxEvent event = new UserOutboxEvent("user.created", "{\"userId\":1}");
        given(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        userOutboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void failedSendLeavesOutboxPending() {
        UserOutboxEvent event = new UserOutboxEvent("user.created", "{\"userId\":1}");
        given(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka down")));

        userOutboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNull();
    }
}
