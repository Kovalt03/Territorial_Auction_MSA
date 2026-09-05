package com.territorial.season.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** combat-events 소비 멱등 기록 — SIEGE_WIN(XP·미션)이 비멱등이라 Kafka 재전송 시 중복 처리를 막는다. */
@Entity
@Table(name = "processed_combat_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedCombatEvent {

    @Id
    @Column(name = "receipt_key", length = 200)
    private String receiptKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ProcessedCombatEvent(String receiptKey) {
        this.receiptKey = receiptKey;
    }
}
