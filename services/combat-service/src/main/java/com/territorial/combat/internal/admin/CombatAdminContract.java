package com.territorial.combat.internal.admin;

import com.territorial.combat.domain.building.entity.BuildingLevelSpec;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.entity.UnitTypeLevelSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;

public final class CombatAdminContract {

    private CombatAdminContract() {}

    public record BuildingTypeCatalog(List<BuildingTypeView> buildingTypes) {}

    public record BuildingTypeView(
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

        public static BuildingTypeView from(BuildingType type) {
            return new BuildingTypeView(
                    type.getId(),
                    type.getName(),
                    type.getDisplayName(),
                    type.getCategory() != null ? type.getCategory().name() : null,
                    type.getWidth(),
                    type.getHeight(),
                    type.getMaxHp(),
                    type.getBaseCostGp(),
                    type.getUpgradeCostGp(),
                    type.getApCost(),
                    type.getZoneRestriction(),
                    type.getDefensePower(),
                    type.getFoodProductionRate(),
                    type.getUnitCapacityPerLevel(),
                    type.getGpProductionRate(),
                    type.getBuildTimeSeconds(),
                    type.getUpgradeTimeSeconds(),
                    type.getIcon(),
                    type.getColorHex());
        }
    }

    public record CreateBuildingTypeRequest(
            @NotBlank String name,
            String displayName,
            @NotNull @Positive Integer width,
            @NotNull @Positive Integer height,
            @NotNull @Positive Integer maxHp,
            @NotNull @PositiveOrZero Integer baseCostGp,
            @PositiveOrZero Integer upgradeCostGp,
            @PositiveOrZero Integer apCost,
            Integer zoneRestriction,
            Integer defensePower,
            Integer foodProductionRate,
            Integer unitCapacityPerLevel,
            Integer gpProductionRate,
            @PositiveOrZero Integer buildTimeSeconds,
            @PositiveOrZero Integer upgradeTimeSeconds,
            String icon,
            String colorHex) {}

    public record UpdateBuildingTypeRequest(
            String displayName,
            @NotNull @Positive Integer width,
            @NotNull @Positive Integer height,
            @NotNull @Positive Integer maxHp,
            @NotNull @PositiveOrZero Integer baseCostGp,
            @PositiveOrZero Integer upgradeCostGp,
            @PositiveOrZero Integer apCost,
            Integer zoneRestriction,
            Integer defensePower,
            Integer foodProductionRate,
            Integer unitCapacityPerLevel,
            Integer gpProductionRate,
            @PositiveOrZero Integer buildTimeSeconds,
            @PositiveOrZero Integer upgradeTimeSeconds,
            String icon,
            String colorHex) {}

    public record LevelSpecValues(
            Integer upgradeCostGp,
            Integer maxHp,
            Integer defensePower,
            Integer foodProductionRate,
            Integer unitCapacityPerLevel,
            Integer gpProductionRate,
            Integer upgradeTimeSeconds) {

        public static LevelSpecValues from(BuildingLevelSpec spec) {
            return new LevelSpecValues(
                    spec.getUpgradeCostGp(),
                    spec.getMaxHp(),
                    spec.getDefensePower(),
                    spec.getFoodProductionRate(),
                    spec.getUnitCapacityPerLevel(),
                    spec.getGpProductionRate(),
                    spec.getUpgradeTimeSeconds());
        }
    }

    public record LevelSpecsRequest(Map<Integer, LevelSpecValues> specs) {}

    public record CastleLimitsRequest(Map<Integer, Integer> limits) {}

    public record UnitTypeView(
            Long unitTypeId,
            String name,
            String displayName,
            String icon,
            String colorHex,
            int attackPower,
            int defensePower,
            int costGp,
            int foodCost,
            int buildingDamage,
            int level) {

        public static UnitTypeView from(UnitType type) {
            return new UnitTypeView(
                    type.getId(),
                    type.getName(),
                    type.getDisplayName(),
                    type.getIcon(),
                    type.getColorHex(),
                    type.getAttackPower(),
                    type.getDefensePower(),
                    type.getCostGp(),
                    type.getFoodCost(),
                    type.getBuildingDamage(),
                    type.getLevel());
        }
    }

    public record UpdateUnitTypeRequest(
            String displayName,
            String icon,
            String colorHex,
            @NotNull @PositiveOrZero Integer attackPower,
            @NotNull @PositiveOrZero Integer defensePower,
            @NotNull @PositiveOrZero Integer costGp,
            @NotNull @PositiveOrZero Integer foodCost,
            @NotNull @PositiveOrZero Integer buildingDamage,
            @NotNull @Positive Integer level) {}

    public record UnitLevelValues(
            Integer attackPower,
            Integer defensePower,
            Integer trainCostFood,
            Integer requiredBarracksLevel) {

        public static UnitLevelValues from(UnitTypeLevelSpec spec) {
            return new UnitLevelValues(
                    spec.getAttackPower(),
                    spec.getDefensePower(),
                    spec.getTrainCostFood(),
                    spec.getRequiredBarracksLevel());
        }

        public boolean hasNoValues() {
            return attackPower == null
                    && defensePower == null
                    && trainCostFood == null
                    && requiredBarracksLevel == null;
        }
    }

    public record UnitLevelSpecsRequest(Map<Integer, UnitLevelValues> specs) {}

    public record UserResourceView(int availableGp, int availableFood) {}

    public record AdjustGpRequest(@NotNull Long userId, int delta, @NotBlank String commandKey) {}

    public record GpBalanceView(int availableGp) {}
}
