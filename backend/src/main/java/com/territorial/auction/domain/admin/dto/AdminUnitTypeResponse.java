package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.military.entity.UnitType;

public record AdminUnitTypeResponse(
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

    public static AdminUnitTypeResponse from(UnitType t) {
        return new AdminUnitTypeResponse(
                t.getId(),
                t.getName(),
                t.getDisplayName(),
                t.getIcon(),
                t.getColorHex(),
                t.getAttackPower(),
                t.getDefensePower(),
                t.getCostGp(),
                t.getFoodCost(),
                t.getBuildingDamage(),
                t.getLevel());
    }
}
