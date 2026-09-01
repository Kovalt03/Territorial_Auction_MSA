package com.territorial.user.event;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 트랜잭셔널 아웃박스 드레인 — 미발행 이벤트를 Kafka 토픽으로 발행. 전송 성공 시에만 published 표시. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserOutboxPublisher {

    public static final String TOPIC = "user-events";
    // 이벤트 종류(user.created/updated/status-changed)는 Kafka 헤더로 실어 소비자가 분기한다.
    public static final String EVENT_TOPIC_HEADER = "event-topic";

    private final UserOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (UserOutboxEvent event :
                outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(TOPIC, event.getId(), event.getPayload());
                record.headers()
                        .add(EVENT_TOPIC_HEADER, event.getTopic().getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(record).get(); // 동기 대기 — 실패 시 published 미표시 후 다음 주기 재시도
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn(
                        "User outbox 발행 실패. eventId={}, topic={}",
                        event.getId(),
                        event.getTopic(),
                        e);
                return;
            }
        }
    }
}
