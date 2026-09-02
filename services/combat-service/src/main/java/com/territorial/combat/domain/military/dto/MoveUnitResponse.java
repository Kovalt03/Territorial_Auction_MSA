package com.territorial.combat.domain.military.dto;

import java.time.LocalDateTime;

public record MoveUnitResponse(
        Integer movedCount, Integer gpRemaining, LocalDateTime moveCompleteAt) {}
