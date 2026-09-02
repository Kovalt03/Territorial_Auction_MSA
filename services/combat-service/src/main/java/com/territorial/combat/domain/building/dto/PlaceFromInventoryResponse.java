package com.territorial.combat.domain.building.dto;

public record PlaceFromInventoryResponse(
        Long buildingId, String buildingType, int posX, int posY, Long territoryId) {}
