package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.global.client.MapAdminClient;
import java.time.LocalDateTime;

public record AdminUserTerritoryResponse(
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        String grade,
        String status,
        LocalDateTime occupiedUntil) {

    public static AdminUserTerritoryResponse from(MapAdminClient.UserTerritoryView v) {
        return new AdminUserTerritoryResponse(
                v.territoryId(),
                v.coordX(),
                v.coordY(),
                v.continentName(),
                v.grade(),
                v.status(),
                v.occupiedUntil());
    }
}
