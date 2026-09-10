package com.territorial.auction.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class AuctionOutboxPublisherTest {

    @InjectMocks private AuctionOutboxPublisher auctionOutboxPublisher;
    @Mock private AuctionOutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @DisplayName("Kafka 전송 성공 → outbox published 표시")
    @Test
    void successfulSendMarksOutboxPublished() {
        AuctionOutboxEvent event = new AuctionOutboxEvent("auction.bid", "{\"auctionId\":1}");
        given(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        auctionOutboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
    }

    @DisplayName("Kafka 전송 실패 → outbox 미발행 유지(다음 주기 재시도)")
    @Test
    void failedSendLeavesOutboxPending() {
        AuctionOutboxEvent event = new AuctionOutboxEvent("auction.bid", "{\"auctionId\":1}");
        given(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("kafka down")));

        auctionOutboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNull();
    }
}
