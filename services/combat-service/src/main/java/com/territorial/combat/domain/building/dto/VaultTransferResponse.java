package com.territorial.combat.domain.building.dto;

import java.time.LocalDateTime;

public record VaultTransferResponse(
        String direction,
        long transferredAmount,
        long sourceTerritoryId,
        long territoryStorageAfter,
        long vaultStoredAfter,
        long vaultCapacity,
        LocalDateTime nextTransferAvailableAt) {}
