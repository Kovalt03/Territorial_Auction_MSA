package com.territorial.combat.domain.building.dto;

public record PurchaseDecorationResponse(
        Long inventoryId, String buildingType, String displayName, int apRemaining) {}
