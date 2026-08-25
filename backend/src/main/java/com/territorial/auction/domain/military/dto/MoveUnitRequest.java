package com.territorial.auction.domain.military.dto;

import com.territorial.auction.domain.military.LocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MoveUnitRequest(
        @NotNull Long unitTypeId,
        @NotNull @Min(1) Integer quantity,
        /** 이동할 유닛 레벨. null이면 1. */
        @Min(1) Integer level,
        @NotNull Long sourceLocationId,
        @NotNull LocationType sourceLocationType,
        @NotNull Long destLocationId,
        @NotNull LocationType destLocationType) {}
