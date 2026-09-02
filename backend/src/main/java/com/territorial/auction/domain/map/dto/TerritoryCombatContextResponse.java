package com.territorial.auction.domain.map.dto;

import com.territorial.auction.domain.map.entity.Territory;
import java.time.LocalDateTime;

public record TerritoryCombatContextResponse(
        Long territoryId,
        Long ownerId,
        int coordX,
        int coordY,
        String status,
        LocalDateTime protectedUntil,
        int gridSize,
        int zone1Radius,
        int zone2Radius) {

    public static TerritoryCombatContextResponse from(Territory territory) {
        return new TerritoryCombatContextResponse(
                territory.getId(),
                territory.getOwner() != null ? territory.getOwner().getId() : null,
                territory.getCoordX(),
                territory.getCoordY(),
                territory.getStatus().name(),
                territory.getProtectedUntil(),
                territory.getGrade().getGridSize(),
                territory.getGrade().getZone1Radius(),
                territory.getGrade().getZone2Radius());
    }
}
