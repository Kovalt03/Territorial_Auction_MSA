package com.territorial.combat.domain.building.dto;

import java.time.LocalDateTime;

public record StoreBuildingResponse(
        Long buildingId, String type, int level, int hp, LocalDateTime storedAt) {}
