package com.territorial.auction.domain.ranking.dto;

import java.util.Map;

public record MyRankingResponse(
        Long seasonId,
        Integer seasonNumber,
        TerritoryHoldSummary territoryHold,
        AuctionSpendSummary auctionSpend) {

    public record TerritoryHoldSummary(
            Integer rank, Long score, Map<String, Long> gradeBreakdown) {}

    public record AuctionSpendSummary(Integer rank, Long totalSpentAP) {}
}
