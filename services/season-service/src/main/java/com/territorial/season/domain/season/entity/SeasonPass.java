package com.territorial.season.domain.season.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "season_passes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer costAp;

    @Column(nullable = false)
    private Integer durationDays = 30;

    @Column(nullable = false)
    private Integer islandBonusPct; // 섬 생산량 보너스 %

    @Column(nullable = false)
    private Integer extraBuilders = 1;

    @Column(nullable = false)
    private Integer taxExemptBonus = 2;

    @Column(nullable = false)
    private Integer buildTimeReductionPct = 0; // 건설·업그레이드 시간 감소 %

    @Builder
    public SeasonPass(
            String name,
            Integer costAp,
            Integer durationDays,
            Integer islandBonusPct,
            Integer extraBuilders,
            Integer taxExemptBonus,
            Integer buildTimeReductionPct) {
        this.name = name;
        this.costAp = costAp;
        this.durationDays = durationDays != null ? durationDays : 30;
        this.islandBonusPct = islandBonusPct;
        this.extraBuilders = extraBuilders != null ? extraBuilders : 1;
        this.taxExemptBonus = taxExemptBonus != null ? taxExemptBonus : 2;
        this.buildTimeReductionPct = buildTimeReductionPct != null ? buildTimeReductionPct : 0;
    }

    // 관리자 편집 — 이름은 식별자라 변경 불가.
    public void update(
            Integer costAp,
            Integer durationDays,
            Integer islandBonusPct,
            Integer extraBuilders,
            Integer taxExemptBonus,
            Integer buildTimeReductionPct) {
        this.costAp = costAp;
        this.durationDays = durationDays;
        this.islandBonusPct = islandBonusPct;
        this.extraBuilders = extraBuilders;
        this.taxExemptBonus = taxExemptBonus;
        this.buildTimeReductionPct = buildTimeReductionPct != null ? buildTimeReductionPct : 0;
    }
}
