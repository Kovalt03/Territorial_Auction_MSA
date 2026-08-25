package com.territorial.auction.domain.military.dto;

import com.territorial.auction.domain.military.LocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DeployUnitRequest(
        @NotNull Long territoryId,
        @NotNull Long buildingId,
        @NotNull Long unitTypeId,
        @NotNull @Min(1) Integer quantity,
        /** 주둔시킬 유닛 레벨. null이면 1. */
        @Min(1) Integer level,
        @NotNull Long sourceLocationId,
        @NotNull LocationType sourceLocationType) {}
