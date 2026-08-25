package com.territorial.auction.domain.map.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TerritoryDetailResponse(
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        String grade,
        BigDecimal gradeMultiplier,
        int gridSize,
        int zone1Radius,
        int zone2Radius,
        String status,
        OwnerInfo owner,
        int baseProductionRate,
        boolean isInvincible,
        List<BuildingInfo> buildings,
        AuctionInfo auction,
        Integer productionRatePerMin,
        LocalDateTime lastProducedAt,
        Integer storedGp,
        Integer storageCapacity) {

    public record OwnerInfo(Long userId, String nickname, String color) {}

    public record BuildingInfo(Long buildingId, String type, int level, int hp, int maxHp) {}

    public record AuctionInfo(Long auctionId, int currentPrice, LocalDateTime endAt) {}
}
