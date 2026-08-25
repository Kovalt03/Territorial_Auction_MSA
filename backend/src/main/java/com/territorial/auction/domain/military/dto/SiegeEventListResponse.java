package com.territorial.auction.domain.military.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SiegeEventListResponse(long totalCount, List<SiegeDto> sieges) {

    public record SiegeDto(
            Long siegeId,
            String status,
            UserDto attacker,
            UserDto defender,
            TerritoryDto targetTerritory,
            Integer attackZone,
            TargetBuildingDto targetBuilding, // null이면 일반(존 전체) 공격
            LocalDateTime siegeStartAt,
            LocalDateTime resolveAt) {}

    public record UserDto(Long userId, String nickname) {}

    public record TerritoryDto(Long id, Integer coordX, Integer coordY) {}

    /** 정밀 공격 대상 건물. 일반 공격이면 null. */
    public record TargetBuildingDto(Long buildingId, String name, String displayName) {}
}
