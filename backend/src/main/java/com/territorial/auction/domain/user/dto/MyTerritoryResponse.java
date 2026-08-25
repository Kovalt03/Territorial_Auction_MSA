package com.territorial.auction.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

public record MyTerritoryResponse(int totalCount, List<TerritoryInfo> territories) {

    public record TerritoryInfo(
            Long territoryId,
            String grade,
            PositionPair position,
            String continentName,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss+09:00") LocalDateTime occupiedAt,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss+09:00") LocalDateTime occupiedUntil,
            int militaryCount,
            boolean isInvincible) {}
}
