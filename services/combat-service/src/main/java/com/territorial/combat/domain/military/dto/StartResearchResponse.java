package com.territorial.combat.domain.military.dto;

import java.time.LocalDateTime;

public record StartResearchResponse(
        Long unitTypeId,
        int pendingLevel,
        LocalDateTime researchCompleteAt,
        int vaultGpRemaining) {}
