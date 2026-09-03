package com.territorial.combat.event;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CombatOutboxPublisher {

    public static final String TOPIC = "combat-events";
    public static final String EVENT_TOPIC_HEADER = "event-topic";
    public static final String EVENT_ID_HEADER = "event-id";

    private final CombatOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (CombatOutboxEvent event :
                repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(TOPIC, event.getAggregateId(), event.getPayload());
                record.headers()
                        .add(
                                EVENT_TOPIC_HEADER,
                                event.getEventTopic().getBytes(StandardCharsets.UTF_8));
                record.headers()
                        .add(EVENT_ID_HEADER, event.getId().getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(record).get();
                event.markPublished();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.warn(
                        "Combat outbox 발행 실패. eventId={}, eventTopic={}",
                        event.getId(),
                        event.getEventTopic(),
                        exception);
                return;
            }
        }
    }
}
