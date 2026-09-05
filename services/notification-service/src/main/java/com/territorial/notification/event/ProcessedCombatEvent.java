package com.territorial.notification.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** combat-events 소비 멱등 기록 — 알림 저장이 비멱등이라 Kafka 재전송 시 중복 알림을 막는다. */
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
