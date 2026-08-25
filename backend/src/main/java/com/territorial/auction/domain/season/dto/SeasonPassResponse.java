package com.territorial.auction.domain.season.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SeasonPassResponse(
        Long seasonId,
        String seasonName,
        String passType,
        Integer currentLevel,
        Integer currentXp,
        Integer nextLevelXp,
        Integer passCostAp,
        Integer levelUpCostAp,
        LocalDateTime seasonEndsAt,
        List<RewardItem> rewards) {
    public record RewardItem(
            Long rewardId,
            Integer level,
            String track,
            String rewardName,
            Boolean isClaimed,
            Boolean canClaim) {}
}
