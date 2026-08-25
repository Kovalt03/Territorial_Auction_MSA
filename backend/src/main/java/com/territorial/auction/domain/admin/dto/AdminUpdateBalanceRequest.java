package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AdminUpdateBalanceRequest(@NotBlank String key, @Min(0) int value) {}
