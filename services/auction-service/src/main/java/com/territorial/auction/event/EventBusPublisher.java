package com.territorial.auction.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 이중 발행 — 회복탄력성 버전.
 *
 * <p>durable 소비자(랭킹·map 프로젝션 등)용 Kafka 발행은 <b>트랜잭셔널 아웃박스</b>로 처리한다: 호출자 트랜잭션 안에서
 * auction_outbox_events에 기록(도메인 변경과 원자적) → {@link AuctionOutboxPublisher} 릴레이가 Kafka로 드레인. 따라서
 * Kafka 장애가 입찰/정산 요청 경로를 실패시키지 않고, 이벤트도 유실 없이 복구 후 전달된다.
 *
 * <p>실시간(WebSocket) 소비자용 Redis pub/sub는 커밋 후 best-effort로 발행한다(저지연, 유실 허용). 반드시 <b>활성 트랜잭션 안에서</b>
 * 호출해야 아웃박스 기록이 원자적으로 커밋된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBusPublisher implements EventPublisher {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final AuctionOutboxEventRepository outboxEventRepository;

    @Override
    public void publish(String topic, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화 실패: topic=" + topic, e);
        }

        // durable — 아웃박스에 원자적으로 기록(호출자 트랜잭션에 참여). 릴레이가 Kafka로 발행.
        outboxEventRepository.save(new AuctionOutboxEvent(topic, json));

        // real-time — 커밋 후 Redis 브로드캐스트(best-effort). 실패해도 요청/트랜잭션에 영향 없음.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publishRealtime(topic, json);
                        }
                    });
        } else {
            publishRealtime(topic, json);
        }
    }

    private void publishRealtime(String topic, String json) {
        try {
            redissonClient.getTopic(topic).publish(json);
        } catch (Exception e) {
            log.warn("auction 실시간(Redis) 브로드캐스트 실패. topic={}", topic, e);
        }
    }
}
