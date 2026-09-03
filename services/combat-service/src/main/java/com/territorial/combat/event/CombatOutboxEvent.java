package com.territorial.combat.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "combat_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CombatOutboxEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String eventTopic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    public CombatOutboxEvent(
            String aggregateType, String aggregateId, String eventTopic, String payload) {
        this.id = UUID.randomUUID().toString();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventTopic = eventTopic;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        publishedAt = LocalDateTime.now();
    }
}
