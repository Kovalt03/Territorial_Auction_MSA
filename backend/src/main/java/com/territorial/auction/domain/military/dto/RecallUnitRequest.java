package com.territorial.auction.domain.military.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecallUnitRequest(
        @NotNull Long territoryId,
        @NotNull Long unitTypeId,
        @NotNull @Min(1) Integer quantity,
        /** 회수할 유닛 레벨. null이면 1. */
        @Min(1) Integer level) {}
