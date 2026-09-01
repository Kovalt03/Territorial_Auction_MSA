package com.territorial.auction.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 이중 발행: 실시간 WebSocket 소비자는 Redis pub/sub(저지연)로, durable 소비자(랭킹·프로젝션)는 Kafka(내구·순서·리플레이)로. 페이로드는
 * JSON 문자열 — 서비스마다 이벤트 클래스가 다르므로 필드 기반 통신.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBusPublisher implements EventPublisher {

    public static final String KAFKA_TOPIC = "auction-events";
    // 이벤트 종류(auction.bid/settled/opened/closed)는 Kafka 헤더로 실어 소비자가 분기한다.
    public static final String EVENT_TOPIC_HEADER = "event-topic";

    private final RedissonClient redissonClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String topic, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화 실패: topic=" + topic, e);
        }
        // 실시간(WebSocket) — Redis pub/sub
        redissonClient.getTopic(topic).publish(json);
        // durable(랭킹·map 프로젝션) — Kafka
        ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, topic, json);
        record.headers().add(EVENT_TOPIC_HEADER, topic.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate
                .send(record)
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                log.warn("auction 이벤트 Kafka 발행 실패. topic={}", topic, ex);
                            }
                        });
    }
}
