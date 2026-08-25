package com.territorial.auction.domain.building.dto;

import com.territorial.auction.domain.building.entity.BuildingType;
import java.util.List;

public record BuildingTypeCatalogResponse(List<BuildingTypeInfo> buildingTypes) {

    public record BuildingTypeInfo(
            Long buildingTypeId,
            String name,
            String displayName,
            String category,
            int width,
            int height,
            int maxHp,
            int baseCostGp,
            Integer upgradeCostGp,
            Integer apCost,
            Integer zoneRestriction,
            Integer defensePower,
            Integer foodProductionRate,
            Integer unitCapacityPerLevel,
            Integer gpProductionRate,
            Integer buildTimeSeconds,
            Integer upgradeTimeSeconds,
            String icon,
            String colorHex) {

        public static BuildingTypeInfo from(BuildingType t) {
            return new BuildingTypeInfo(
                    t.getId(),
                    t.getName(),
                    t.getDisplayName(),
                    t.getCategory() != null ? t.getCategory().name() : null,
                    t.getWidth(),
                    t.getHeight(),
                    t.getMaxHp(),
                    t.getBaseCostGp(),
                    t.getUpgradeCostGp(),
                    t.getApCost(),
                    t.getZoneRestriction(),
                    t.getDefensePower(),
                    t.getFoodProductionRate(),
                    t.getUnitCapacityPerLevel(),
                    t.getGpProductionRate(),
                    t.getBuildTimeSeconds(),
                    t.getUpgradeTimeSeconds(),
                    t.getIcon(),
                    t.getColorHex());
        }
    }

    public static BuildingTypeCatalogResponse of(List<BuildingType> types) {
        return new BuildingTypeCatalogResponse(types.stream().map(BuildingTypeInfo::from).toList());
    }
}
