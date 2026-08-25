package com.territorial.auction.domain.map.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "bonus_tiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BonusTile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "territory_id", unique = true, nullable = false)
    private Territory territory;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal multiplier;

    @Column(length = 100)
    private String description;

    @Builder
    public BonusTile(Territory territory, BigDecimal multiplier, String description) {
        this.territory = territory;
        this.multiplier = multiplier;
        this.description = description;
    }
}
