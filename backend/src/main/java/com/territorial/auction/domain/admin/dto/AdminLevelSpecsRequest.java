package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.building.entity.BuildingLevelSpec;
import java.util.Map;

// {도달레벨: 값들}. 각 값이 null이면 해당 항목은 공식 폴백.
public record AdminLevelSpecsRequest(Map<Integer, LevelSpecValues> specs) {

    public record LevelSpecValues(
            Integer upgradeCostGp,
            Integer maxHp,
            Integer defensePower,
            Integer foodProductionRate,
            Integer unitCapacityPerLevel,
            Integer gpProductionRate,
            Integer upgradeTimeSeconds) {

        public static LevelSpecValues from(BuildingLevelSpec s) {
            return new LevelSpecValues(
                    s.getUpgradeCostGp(),
                    s.getMaxHp(),
                    s.getDefensePower(),
                    s.getFoodProductionRate(),
                    s.getUnitCapacityPerLevel(),
                    s.getGpProductionRate(),
                    s.getUpgradeTimeSeconds());
        }
    }
}
