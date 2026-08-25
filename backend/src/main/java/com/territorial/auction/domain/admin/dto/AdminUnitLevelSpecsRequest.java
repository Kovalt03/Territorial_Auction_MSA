package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.military.entity.UnitTypeLevelSpec;
import java.util.Map;

// {도달레벨: 값들}. 값이 null이면 그 레벨의 훈련을 없앤다.
public record AdminUnitLevelSpecsRequest(Map<Integer, UnitLevelValues> specs) {

    public record UnitLevelValues(
            Integer attackPower,
            Integer defensePower,
            Integer trainCostFood,
            Integer requiredBarracksLevel) {

        public static UnitLevelValues from(UnitTypeLevelSpec s) {
            return new UnitLevelValues(
                    s.getAttackPower(),
                    s.getDefensePower(),
                    s.getTrainCostFood(),
                    s.getRequiredBarracksLevel());
        }

        public boolean hasNoValues() {
            return attackPower == null
                    && defensePower == null
                    && trainCostFood == null
                    && requiredBarracksLevel == null;
        }
    }
}
