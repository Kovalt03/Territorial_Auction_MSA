package com.territorial.combat.domain.military.dto;

import com.territorial.combat.domain.military.LocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DeployUnitRequest(
        @NotNull Long territoryId,
        @NotNull Long buildingId,
        @NotNull Long unitTypeId,
        @NotNull @Min(1) Integer quantity,
        @Min(1) Integer level,
        @NotNull Long sourceLocationId,
        @NotNull LocationType sourceLocationType) {}
