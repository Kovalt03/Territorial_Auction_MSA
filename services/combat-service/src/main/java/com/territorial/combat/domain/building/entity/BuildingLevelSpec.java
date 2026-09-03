package com.territorial.combat.domain.building.entity;

import jakarta.persistence.*;
import lombok.*;

// 건물별·레벨별 세부 설정. 현재는 레벨별 업그레이드 비용만. (없으면 공식 폴백)
@Entity
@Table(
        name = "building_level_specs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"building_type_id", "level"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingLevelSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_type_id", nullable = false)
    private BuildingType buildingType;

    @Column(nullable = false)
    private Integer level; // 도달 레벨(2..MAX). 해당 레벨로 올리는 비용/스탯

    // 아래 값들은 절대값. NULL이면 각 기본 공식으로 폴백한다.
    @Column private Integer upgradeCostGp; // 이 레벨로 올리는 비용
    @Column private Integer maxHp; // 이 레벨 최대 HP
    @Column private Integer defensePower; // 이 레벨 방어력
    @Column private Integer foodProductionRate; // 이 레벨 식량/시간
    @Column private Integer unitCapacityPerLevel; // 이 레벨 유닛 수용량
    @Column private Integer gpProductionRate; // 이 레벨 GP/시간
    @Column private Integer upgradeTimeSeconds; // 이 레벨로 올리는 데 걸리는 시간(초)

    @Builder
    public BuildingLevelSpec(
            BuildingType buildingType,
            Integer level,
            Integer upgradeCostGp,
            Integer maxHp,
            Integer defensePower,
            Integer foodProductionRate,
            Integer unitCapacityPerLevel,
            Integer gpProductionRate,
            Integer upgradeTimeSeconds) {
        this.buildingType = buildingType;
        this.level = level;
        this.upgradeCostGp = upgradeCostGp;
        this.maxHp = maxHp;
        this.defensePower = defensePower;
        this.foodProductionRate = foodProductionRate;
        this.unitCapacityPerLevel = unitCapacityPerLevel;
        this.gpProductionRate = gpProductionRate;
        this.upgradeTimeSeconds = upgradeTimeSeconds;
    }

    public void update(
            Integer upgradeCostGp,
            Integer maxHp,
            Integer defensePower,
            Integer foodProductionRate,
            Integer unitCapacityPerLevel,
            Integer gpProductionRate,
            Integer upgradeTimeSeconds) {
        this.upgradeCostGp = upgradeCostGp;
        this.maxHp = maxHp;
        this.defensePower = defensePower;
        this.foodProductionRate = foodProductionRate;
        this.unitCapacityPerLevel = unitCapacityPerLevel;
        this.gpProductionRate = gpProductionRate;
        this.upgradeTimeSeconds = upgradeTimeSeconds;
    }

    // 지정된 값이 하나도 없으면 빈 스펙(삭제 대상)
    public boolean isEmpty() {
        return upgradeCostGp == null
                && maxHp == null
                && defensePower == null
                && foodProductionRate == null
                && unitCapacityPerLevel == null
                && gpProductionRate == null
                && upgradeTimeSeconds == null;
    }
}
