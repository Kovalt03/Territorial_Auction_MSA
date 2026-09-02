package com.territorial.combat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class CombatOutboxPublisherTest {

    @InjectMocks private CombatOutboxPublisher publisher;
    @Mock private CombatOutboxEventRepository repository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void successfulSendMarksPublishedAndUsesAggregateKey() {
        CombatOutboxEvent event =
                new CombatOutboxEvent("SIEGE", "10", "combat.siege.declared", "{}");
        given(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.BDDMockito.then(kafkaTemplate).should().send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("combat-events");
        assertThat(captor.getValue().key()).isEqualTo("10");
        assertThat(
                        new String(
                                captor.getValue().headers().lastHeader("event-topic").value(),
                                StandardCharsets.UTF_8))
                .isEqualTo("combat.siege.declared");
    }

    @Test
    void failedSendLeavesPending() {
        CombatOutboxEvent event =
                new CombatOutboxEvent("SIEGE", "10", "combat.siege.declared", "{}");
        given(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));

        publisher.publishPending();

        assertThat(event.getPublishedAt()).isNull();
    }
}
