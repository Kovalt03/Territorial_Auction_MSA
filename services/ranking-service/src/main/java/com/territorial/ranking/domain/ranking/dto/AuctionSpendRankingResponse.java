package com.territorial.ranking.domain.ranking.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuctionSpendRankingResponse(
        Long seasonId,
        Integer seasonNumber,
        String type,
        List<RankEntry> rankings,
        Integer myRank,
        Long myScore,
        LocalDateTime updatedAt) {

    public record RankEntry(int rank, Long userId, String nickname, long totalSpentAP) {}
}
