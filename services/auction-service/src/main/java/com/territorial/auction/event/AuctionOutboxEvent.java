package com.territorial.auction.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 트랜잭셔널 아웃박스 레코드. 입찰/정산 트랜잭션 안에서 저장되어 도메인 변경과 원자적으로 커밋되고, 릴레이가 Kafka로 발행 후 publishedAt을 찍는다. topic은
 * 이벤트 종류(auction.bid/opened/closed/settled) — Kafka 헤더로 실린다.
 */
@Entity
@Table(name = "auction_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionOutboxEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    public AuctionOutboxEvent(String topic, String payload) {
        this.id = UUID.randomUUID().toString();
        this.topic = topic;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }
}
