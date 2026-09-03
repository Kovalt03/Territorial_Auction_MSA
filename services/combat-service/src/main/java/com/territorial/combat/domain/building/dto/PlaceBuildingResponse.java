package com.territorial.combat.domain.building.dto;

public record PlaceBuildingResponse(
        Long buildingId, String type, int posX, int posY, int gpRemaining) {}
