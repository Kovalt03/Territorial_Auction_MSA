package com.territorial.auction.domain.military.entity;

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

    // 이 공성 실패 후 적용될 공격 쿨다운(시간). 보급소로 완화된 값이 반영된다. null이면 기본값 사용.
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
