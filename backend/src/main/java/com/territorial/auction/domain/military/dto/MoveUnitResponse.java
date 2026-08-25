package com.territorial.auction.domain.military.dto;

import java.time.LocalDateTime;

public record MoveUnitResponse(
        Integer movedCount, Integer gpRemaining, LocalDateTime moveCompleteAt) {}
