package com.territorial.combat.domain.military.dto;

public record GarrisonUnitResponse(
        Long unitTypeId,
        String name,
        String displayName,
        String icon,
        String colorHex,
        Integer deployedCount) {}
