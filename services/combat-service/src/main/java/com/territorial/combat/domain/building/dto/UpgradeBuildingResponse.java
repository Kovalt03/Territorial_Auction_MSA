package com.territorial.combat.domain.building.dto;

import java.time.LocalDateTime;

public record UpgradeBuildingResponse(
        Long buildingId,
        int newLevel,
        Integer nextLevel,
        int maxLevel,
        int upgradeCost,
        int gpRemaining,
        LocalDateTime buildCompleteAt) {}
