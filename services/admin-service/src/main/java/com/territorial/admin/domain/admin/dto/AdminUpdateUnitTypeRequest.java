package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// 이름은 서버 식별자라 변경 불가 — 수치와 표시 정보만 수정한다.
public record AdminUpdateUnitTypeRequest(
        String displayName,
        String icon,
        String colorHex,
        @NotNull @PositiveOrZero Integer attackPower,
        @NotNull @PositiveOrZero Integer defensePower,
        @NotNull @PositiveOrZero Integer costGp,
        @NotNull @PositiveOrZero Integer foodCost,
        @NotNull @PositiveOrZero Integer buildingDamage,
        @NotNull @Positive Integer level) {}
