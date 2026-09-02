package com.territorial.combat.domain.military.dto;

import com.territorial.combat.domain.military.entity.UnitType;

public record ProduceUnitResponse(
        Long unitTypeId, String unitName, Integer quantity, Integer gpRemaining) {
    public static ProduceUnitResponse of(UnitType unitType, Integer quantity, Integer gpRemaining) {
        return new ProduceUnitResponse(unitType.getId(), unitType.getName(), quantity, gpRemaining);
    }
}
