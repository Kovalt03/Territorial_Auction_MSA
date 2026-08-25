package com.territorial.auction.domain.military.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 계정 연구 현황. {@code researchLabLevel}이 연구 가능 상한(+1)을 결정한다. */
public record ResearchStatusResponse(int researchLabLevel, List<UnitResearchDto> units) {

    public record UnitResearchDto(
            Long unitTypeId,
            String name,
            String displayName,
            String icon,
            String colorHex,
            int researchedLevel,
            int maxLevel,
            Integer pendingLevel,
            LocalDateTime researchCompleteAt,
            Integer nextCostGp) {}
}
