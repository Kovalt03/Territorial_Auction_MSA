package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.map.entity.Territory;

public record AdminTerritoryResponse(
        Long territoryId,
        int coordX,
        int coordY,
        String grade,
        String status,
        String ownerNickname,
        boolean auctionEnabled) {

    public static AdminTerritoryResponse from(Territory t) {
        return new AdminTerritoryResponse(
                t.getId(),
                t.getCoordX(),
                t.getCoordY(),
                t.getGrade().getGrade(),
                t.getStatus().name(),
                t.getOwner() != null ? t.getOwner().getNickname() : null,
                t.getAuctionEnabled());
    }
}
