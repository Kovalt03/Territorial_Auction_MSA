package com.territorial.combat.domain.building.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VaultTransferRequest(
        @NotBlank String direction,
        @NotNull Long sourceTerritoryId,
        @NotNull @Min(1) Long amount) {}
