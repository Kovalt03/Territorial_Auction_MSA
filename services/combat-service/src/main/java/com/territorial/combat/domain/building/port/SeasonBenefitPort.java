package com.territorial.combat.domain.building.port;

public interface SeasonBenefitPort {

    SeasonBenefit findActiveBenefit(Long userId);

    record SeasonBenefit(int buildTimeReductionPct, int extraBuilders) {
        public static SeasonBenefit none() {
            return new SeasonBenefit(0, 0);
        }
    }
}
