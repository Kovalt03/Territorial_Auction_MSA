package com.territorial.auction.domain.military.dto;

import com.territorial.auction.domain.military.LocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProduceUnitRequest(
        @NotNull Long unitTypeId,
        @NotNull @Min(1) Integer quantity,
        /** 생산할 유닛 레벨(연구로 해금된 레벨 이하). null이면 1. */
        @Min(1) Integer level,
        @NotNull Long locationId,
        @NotNull LocationType locationType) {}
