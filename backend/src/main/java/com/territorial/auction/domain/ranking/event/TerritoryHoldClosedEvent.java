package com.territorial.auction.domain.ranking.event;

import java.time.LocalDateTime;

public record TerritoryHoldClosedEvent(
        Long userId, Long seasonId, Long territoryId, LocalDateTime heldUntil) {}
