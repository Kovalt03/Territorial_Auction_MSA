package com.territorial.auction.domain.military.entity;

import jakarta.persistence.*;
import lombok.*;

/** 유닛 종류별·레벨별 훈련 스펙. 행이 없으면 그 레벨로 훈련할 수 없다. */
@Entity
@Table(
        name = "unit_type_level_specs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"unit_type_id", "level"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitTypeLevelSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private UnitType unitType;

    @Column(nullable = false)
    private Integer level; // 도달 레벨 (2..MAX_LEVEL)

    @Column(nullable = false)
    private Integer attackPower;

    @Column(nullable = false)
    private Integer defensePower;

    /** 이 레벨로 올리는 데 유닛 1마리당 드는 식량 */
    @Column(nullable = false)
    private Integer trainCostFood;

    /** 훈련에 필요한 최소 병영 레벨 */
    @Column(nullable = false)
    private Integer requiredBarracksLevel;

    @Builder
    public UnitTypeLevelSpec(
            UnitType unitType,
            Integer level,
            Integer attackPower,
            Integer defensePower,
            Integer trainCostFood,
            Integer requiredBarracksLevel) {
        this.unitType = unitType;
        this.level = level;
        this.attackPower = attackPower;
        this.defensePower = defensePower;
        this.trainCostFood = trainCostFood;
        this.requiredBarracksLevel = requiredBarracksLevel;
    }

    public void update(
            Integer attackPower,
            Integer defensePower,
            Integer trainCostFood,
            Integer requiredBarracksLevel) {
        this.attackPower = attackPower;
        this.defensePower = defensePower;
        this.trainCostFood = trainCostFood;
        this.requiredBarracksLevel = requiredBarracksLevel;
    }
}
