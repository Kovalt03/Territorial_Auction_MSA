package com.territorial.ranking.domain.ranking.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TerritoryHoldRankingResponse(
        Long seasonId,
        Integer seasonNumber,
        String type,
        List<RankEntry> rankings,
        Integer myRank,
        Long myScore,
        LocalDateTime updatedAt) {

    public record RankEntry(
            int rank, Long userId, String nickname, long score, Map<String, Long> gradeBreakdown) {}
}
