package com.territorial.admin.domain.admin.dto;

import java.util.List;

public record AdminBuildingTypeCatalogResponse(List<BuildingTypeInfo> buildingTypes) {

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
            String colorHex) {}
}
