package com.territorial.auction.domain.map.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.military.event.TerritoryLostEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class TerritoryLostKafkaPublisherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    private TerritoryLostKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new TerritoryLostKafkaPublisher(new ObjectMapper(), kafkaTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesTerritoryEventWithAggregateKeyAndHeader() {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        publisher.publish(new TerritoryLostEvent(10L, 2L));

        ArgumentCaptor<ProducerRecord<String, String>> record =
                ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo("territory-events");
        assertThat(record.getValue().key()).isEqualTo("10");
        assertThat(
                        new String(
                                record.getValue().headers().lastHeader("event-topic").value(),
                                StandardCharsets.UTF_8))
                .isEqualTo("territory.lost");
        assertThat(record.getValue().value()).contains("\"formerOwnerId\":2");
    }
}
