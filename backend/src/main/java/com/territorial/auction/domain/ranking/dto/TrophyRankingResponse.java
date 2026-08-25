package com.territorial.auction.domain.ranking.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TrophyRankingResponse(
        Long seasonId,
        Integer seasonNumber,
        String type,
        List<RankEntry> rankings,
        Integer myRank,
        Long myScore,
        String myLeague,
        LocalDateTime updatedAt) {

    public record RankEntry(int rank, Long userId, String nickname, int score, String league) {}
}
