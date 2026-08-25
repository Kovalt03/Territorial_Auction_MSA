package com.territorial.auction.domain.ranking.event;

import java.time.LocalDateTime;

public record TerritoryHoldStartedEvent(
        Long userId, Long seasonId, Long territoryId, String grade, LocalDateTime heldFrom) {}
