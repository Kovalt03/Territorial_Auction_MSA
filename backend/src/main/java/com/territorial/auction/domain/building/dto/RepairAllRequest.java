package com.territorial.auction.domain.building.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 전체 수리 요청 — 대상 위치. locationType: "TERRITORY" | "ISLAND". */
public record RepairAllRequest(@NotBlank String locationType, @NotNull Long locationId) {}
