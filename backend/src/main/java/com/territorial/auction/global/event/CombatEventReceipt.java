package com.territorial.auction.global.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "combat_event_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CombatEventReceipt {

    @Id
    @Column(length = 200)
    private String receiptKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public CombatEventReceipt(String receiptKey) {
        this.receiptKey = receiptKey;
        this.processedAt = LocalDateTime.now();
    }
}
