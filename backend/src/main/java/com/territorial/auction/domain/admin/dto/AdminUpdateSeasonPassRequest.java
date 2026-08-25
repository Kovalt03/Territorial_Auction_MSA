package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// 이름은 식별자라 변경 불가 — 수치만 수정한다.
public record AdminUpdateSeasonPassRequest(
        @NotNull @PositiveOrZero Integer costAp,
        @NotNull @Positive Integer durationDays,
        @NotNull @PositiveOrZero Integer islandBonusPct,
        @NotNull @PositiveOrZero Integer extraBuilders,
        @NotNull @PositiveOrZero Integer taxExemptBonus,
        @NotNull @PositiveOrZero @Max(100) Integer buildTimeReductionPct) {}
