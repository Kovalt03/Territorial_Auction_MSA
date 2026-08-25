package com.territorial.auction.domain.admin.dto;

import java.util.List;
import java.util.Map;

public record AdminContinentCompositionResponse(List<ContinentComposition> continents) {

    public record ContinentComposition(
            Long continentId,
            String name,
            Integer minTrophyRequired,
            long totalTerritories,
            Map<String, Long> gradeBreakdown,
            long biddingCount,
            long occupiedCount,
            long idleCount) {}
}
