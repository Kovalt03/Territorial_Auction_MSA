package com.territorial.ranking.domain.ranking.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 이벤트 소비 멱등 기록 — auction.settled(XP·영토보유·경매소비)가 비멱등이라 Kafka 재전송 시 중복 처리를 막는다. */
@Entity
@Table(name = "ranking_processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingProcessedEvent {

    @Id
    @Column(name = "event_key", length = 200)
    private String eventKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public RankingProcessedEvent(String eventKey) {
        this.eventKey = eventKey;
    }
}
