package com.territorial.map.domain.map.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "land_tax_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandTaxLog {

    public enum TaxStatus {
        PAID,
        FAILED,
        EXEMPT,
        EVICTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer territoryCount;

    @Column(nullable = false)
    private Integer gpCharged;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaxStatus status;

    @Column(nullable = false)
    private LocalDateTime chargedAt;

    @Builder
    public LandTaxLog(
            Long userId,
            Integer territoryCount,
            Integer gpCharged,
            TaxStatus status,
            LocalDateTime chargedAt) {
        this.userId = userId;
        this.territoryCount = territoryCount;
        this.gpCharged = gpCharged;
        this.status = status;
        this.chargedAt = chargedAt;
    }
}
