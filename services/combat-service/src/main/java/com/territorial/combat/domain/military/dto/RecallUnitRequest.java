package com.territorial.combat.domain.military.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecallUnitRequest(
        @NotNull Long territoryId,
        @NotNull Long unitTypeId,
        @NotNull @Min(1) Integer quantity,
        @Min(1) Integer level) {}
