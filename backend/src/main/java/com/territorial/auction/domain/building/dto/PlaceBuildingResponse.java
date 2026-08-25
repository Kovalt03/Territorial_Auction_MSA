package com.territorial.auction.domain.building.dto;

public record PlaceBuildingResponse(
        Long buildingId, String type, int posX, int posY, int gpRemaining) {}
