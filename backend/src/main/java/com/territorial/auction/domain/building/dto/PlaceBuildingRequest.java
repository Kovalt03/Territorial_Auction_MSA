package com.territorial.auction.domain.building.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceBuildingRequest(
        @NotNull Long buildingTypeId, @Min(0) int posX, @Min(0) int posY) {}
