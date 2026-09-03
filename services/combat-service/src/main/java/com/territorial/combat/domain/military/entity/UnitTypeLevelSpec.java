package com.territorial.combat.domain.military.entity;

import jakarta.persistence.*;
import lombok.*;

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
    private Integer level;

    @Column(nullable = false)
    private Integer attackPower;

    @Column(nullable = false)
    private Integer defensePower;

    @Column(nullable = false)
    private Integer trainCostFood;

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
