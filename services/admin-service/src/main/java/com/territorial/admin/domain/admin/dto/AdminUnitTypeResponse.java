package com.territorial.admin.domain.admin.dto;

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
        int level) {}
