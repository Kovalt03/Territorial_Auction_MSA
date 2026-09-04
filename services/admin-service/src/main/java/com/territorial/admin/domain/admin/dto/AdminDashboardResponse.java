package com.territorial.admin.domain.admin.dto;

import java.time.LocalDateTime;

public record AdminDashboardResponse(
        long totalUsers,
        long activeUsers,
        long suspendedUsers,
        long activeAuctions,
        long biddingTerritories,
        long occupiedTerritories,
        long idleTerritories,
        long totalAvailableAp,
        long totalAvailableGp,
        Integer currentSeasonNumber,
        LocalDateTime currentSeasonStartedAt,
        LocalDateTime currentSeasonEndedAt) {}
