package com.territorial.auction.domain.map.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.military.event.TerritoryLostEvent;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerritoryLostKafkaPublisher {

    public static final String TOPIC = "territory-events";
    public static final String EVENT_TOPIC = "territory.lost";

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TerritoryLostEvent event) {
        try {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                            TOPIC,
                            String.valueOf(event.territoryId()),
                            objectMapper.writeValueAsString(
                                    new Payload(event.territoryId(), event.formerOwnerId())));
            record.headers().add("event-topic", EVENT_TOPIC.getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record);
        } catch (JsonProcessingException exception) {
            log.error("영토 상실 이벤트 직렬화 실패. territoryId={}", event.territoryId(), exception);
            throw new IllegalStateException("territory.lost 직렬화 실패", exception);
        }
    }

    private record Payload(Long territoryId, Long formerOwnerId) {}
}
