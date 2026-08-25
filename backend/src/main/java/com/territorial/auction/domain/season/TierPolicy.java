package com.territorial.auction.domain.season;

import com.territorial.auction.domain.season.entity.UserTrophy.League;

public final class TierPolicy {

    private record SubTierEntry(int minScore, League league) {}

    // 13 sub-tiers: Bronze3/2/1, Silver3/2/1, Gold3/2/1, Diamond3/2/1, Champion
    private static final SubTierEntry[] TIERS = {
        new SubTierEntry(0, League.BRONZE),
        new SubTierEntry(167, League.BRONZE),
        new SubTierEntry(334, League.BRONZE),
        new SubTierEntry(500, League.SILVER),
        new SubTierEntry(833, League.SILVER),
        new SubTierEntry(1167, League.SILVER),
        new SubTierEntry(1500, League.GOLD),
        new SubTierEntry(2167, League.GOLD),
        new SubTierEntry(2834, League.GOLD),
        new SubTierEntry(4000, League.DIAMOND),
        new SubTierEntry(5334, League.DIAMOND),
        new SubTierEntry(6667, League.DIAMOND),
        new SubTierEntry(8000, League.CHAMPION),
    };

    private TierPolicy() {}

    // Returns the score after one sub-tier demotion (to the lower sub-tier's minimum score)
    public static int calculateResetScore(int currentScore) {
        int idx = findTierIndex(currentScore);
        return idx == 0 ? 0 : TIERS[idx - 1].minScore();
    }

    public static League calculateLeague(int score) {
        return TIERS[findTierIndex(score)].league();
    }

    private static int findTierIndex(int score) {
        for (int i = TIERS.length - 1; i >= 0; i--) {
            if (score >= TIERS[i].minScore()) return i;
        }
        return 0;
    }
}
