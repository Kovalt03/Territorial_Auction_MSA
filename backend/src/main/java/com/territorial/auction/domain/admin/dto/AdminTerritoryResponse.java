package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.global.client.MapAdminClient;

public record AdminTerritoryResponse(
        Long territoryId,
        int coordX,
        int coordY,
        String grade,
        String status,
        String ownerNickname,
        boolean auctionEnabled) {

    public static AdminTerritoryResponse from(MapAdminClient.TerritoryView v) {
        return new AdminTerritoryResponse(
                v.territoryId(),
                v.coordX(),
                v.coordY(),
                v.grade(),
                v.status(),
                v.ownerNickname(),
                v.auctionEnabled());
    }
}
