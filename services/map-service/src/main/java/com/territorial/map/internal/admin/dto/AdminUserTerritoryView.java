package com.territorial.map.internal.admin.dto;

import com.territorial.map.domain.map.entity.Continent;
import com.territorial.map.domain.map.entity.Territory;
import java.time.LocalDateTime;

public record AdminUserTerritoryView(
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        String grade,
        String status,
        LocalDateTime occupiedUntil) {

    public static AdminUserTerritoryView from(Territory t) {
        return new AdminUserTerritoryView(
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
