package com.territorial.combat.domain.military.dto;

import java.time.LocalDateTime;
import java.util.List;

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
