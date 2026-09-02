package com.territorial.combat.domain.military.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "siege_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiegeResult {

    public enum ResultType {
        LOOT,
        DEBUFF,
        AUCTION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "siege_id", nullable = false, unique = true)
    private SiegeEvent siege;

    @Column(nullable = false)
    private Boolean isAttackerWin;

    private Integer attackerUnitsLost;
    private Integer defenderUnitsLost;

    @Column(nullable = false)
    private Integer lootedGp = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private ResultType resultType;

    private Integer appliedCooldownHours;

    @Builder
    public SiegeResult(
            SiegeEvent siege,
            Boolean isAttackerWin,
            Integer attackerUnitsLost,
            Integer defenderUnitsLost,
            Integer lootedGp,
            ResultType resultType,
            Integer appliedCooldownHours) {
        this.siege = siege;
        this.isAttackerWin = isAttackerWin;
        this.attackerUnitsLost = attackerUnitsLost;
        this.defenderUnitsLost = defenderUnitsLost;
        this.lootedGp = lootedGp != null ? lootedGp : 0;
        this.resultType = resultType;
        this.appliedCooldownHours = appliedCooldownHours;
    }
}
