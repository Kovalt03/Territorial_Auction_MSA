package com.territorial.auction.domain.season.dto;

public record CombatSeasonBenefitResponse(int buildTimeReductionPct, int extraBuilders) {

    public static CombatSeasonBenefitResponse none() {
        return new CombatSeasonBenefitResponse(0, 0);
    }
}
