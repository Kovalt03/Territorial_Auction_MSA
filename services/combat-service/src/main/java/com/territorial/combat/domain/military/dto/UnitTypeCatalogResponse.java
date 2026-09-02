package com.territorial.combat.domain.military.dto;

import com.territorial.combat.domain.military.entity.UnitType;

public record UnitTypeCatalogResponse(
        Long unitTypeId,
        String name,
        String displayName,
        String icon,
        String colorHex,
        Integer attackPower,
        Integer defensePower,
        Integer costGp,
        Integer foodCost,
        Integer buildingDamage,
        Integer requiredBarracksLevel) {
    public static UnitTypeCatalogResponse from(UnitType unitType) {
        return new UnitTypeCatalogResponse(
                unitType.getId(),
                unitType.getName(),
                unitType.getDisplayName(),
                unitType.getIcon(),
                unitType.getColorHex(),
                unitType.getAttackPower(),
                unitType.getDefensePower(),
                unitType.getCostGp(),
                unitType.getFoodCost(),
                unitType.getBuildingDamage(),
                unitType.getLevel());
    }
}
