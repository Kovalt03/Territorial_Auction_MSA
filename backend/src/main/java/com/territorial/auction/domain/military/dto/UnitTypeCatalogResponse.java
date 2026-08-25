package com.territorial.auction.domain.military.dto;

import com.territorial.auction.domain.military.entity.UnitType;

/** 훈련 가능한 유닛 종류 카탈로그. 소유 여부와 무관하게 전체 종류를 노출한다(생산 UI 소스). */
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
