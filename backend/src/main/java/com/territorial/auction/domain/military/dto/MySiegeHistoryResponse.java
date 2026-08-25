package com.territorial.auction.domain.military.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MySiegeHistoryResponse(
        long totalCount, long wins, long losses, List<HistoryDto> history) {

    public record HistoryDto(
            Long siegeId,
            Long territoryId,
            String territoryGrade,
            String role,
            String result,
            LocalDateTime occurredAt) {}
}
