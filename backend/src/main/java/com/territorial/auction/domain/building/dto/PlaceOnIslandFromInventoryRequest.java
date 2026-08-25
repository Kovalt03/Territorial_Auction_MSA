package com.territorial.auction.domain.building.dto;

import jakarta.validation.constraints.Min;

public record PlaceOnIslandFromInventoryRequest(@Min(0) int posX, @Min(0) int posY) {}
