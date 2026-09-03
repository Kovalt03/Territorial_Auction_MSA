package com.territorial.combat.domain.building.dto;

import java.time.LocalDateTime;

public record HarvestIslandGpResponse(
        int harvestedGp, int gpBalance, LocalDateTime lastHarvestAt) {}
