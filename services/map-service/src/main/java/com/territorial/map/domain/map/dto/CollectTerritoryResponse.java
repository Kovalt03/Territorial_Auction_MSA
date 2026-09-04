package com.territorial.map.domain.map.dto;

import java.time.LocalDateTime;

public record CollectTerritoryResponse(
        int creditedGp,
        int storedGp,
        int productionRatePerMin,
        LocalDateTime lastProducedAt,
        int storageCapacity) {}
