package com.territorial.combat.domain.building.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceFromInventoryRequest(
        @NotNull Long territoryId, @Min(0) int posX, @Min(0) int posY) {}
