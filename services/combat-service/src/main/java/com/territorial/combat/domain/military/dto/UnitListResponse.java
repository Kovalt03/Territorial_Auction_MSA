package com.territorial.combat.domain.military.dto;

import java.util.List;

public record UnitListResponse(List<LocationUnits> locations) {
    public record LocationUnits(
            String locationType,
            Long locationId,
            Integer coordX,
            Integer coordY,
            Integer unitCapacity,
            Integer storedFood,
            List<UnitDto> units) {}

    public record UnitDto(
            Long unitTypeId,
            String name,
            String displayName,
            String icon,
            String colorHex,
            Integer quantity,
            Integer deployedCount,
            Integer idleCount,
            Integer inTransitCount,
            Integer attackPower,
            Integer defensePower,
            Integer costGp,
            Integer foodCost,
            Integer buildingDamage,
            Integer requiredBarracksLevel) {}
}
