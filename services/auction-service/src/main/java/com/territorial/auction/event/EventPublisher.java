package com.territorial.auction.event;

/**
 * 서비스 간 비동기 이벤트 발행 포트. 구현체는 Redis pub/sub(Redisson RTopic 등). 구독은 각 소비 서비스가 담당 —
 * auction-migration-tracking.md §1 참고.
 */
public interface EventPublisher {
    void publish(String topic, Object payload);
}
