package com.territorial.auction.domain.map.dto;

import java.util.List;

public record GridMapResponse(int mapSize, List<GridTerritoryDto> territories) {

    public record GridTerritoryDto(
            Long territoryId,
            int coordX,
            int coordY,
            Long ownerId,
            String ownerNickname,
            String currentColor,
            String grade,
            String status,
            boolean hasActiveAuction,
            Long continentId,
            int gridSize) {}
}
