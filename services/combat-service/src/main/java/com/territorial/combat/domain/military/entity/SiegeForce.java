package com.territorial.combat.domain.military.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "siege_forces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiegeForce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "siege_id", nullable = false)
    private SiegeEvent siege;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private UnitType unitType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer level = 1;

    @Builder
    public SiegeForce(SiegeEvent siege, UnitType unitType, Integer quantity, Integer level) {
        this.siege = siege;
        this.unitType = unitType;
        this.quantity = quantity;
        this.level = level != null ? level : 1;
    }

    public void subtractQuantity(int amount) {
        quantity -= amount;
    }
}
