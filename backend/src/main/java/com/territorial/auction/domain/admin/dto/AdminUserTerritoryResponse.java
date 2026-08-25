package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import java.time.LocalDateTime;

public record AdminUserTerritoryResponse(
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        String grade,
        String status,
        LocalDateTime occupiedUntil) {

    public static AdminUserTerritoryResponse from(Territory t) {
        return new AdminUserTerritoryResponse(
                t.getId(),
                t.getCoordX(),
                t.getCoordY(),
                continentName(t.getContinent()),
                t.getGrade().getGrade(),
                t.getStatus().name(),
                t.getOccupiedUntil());
    }

    private static String continentName(Continent c) {
        return c.getDisplayName() != null ? c.getDisplayName() : c.getName();
    }
}
