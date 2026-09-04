package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// 이름은 게임 로직 식별자라 변경 불가 — 스탯만 수정.
public record AdminUpdateBuildingTypeRequest(
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
