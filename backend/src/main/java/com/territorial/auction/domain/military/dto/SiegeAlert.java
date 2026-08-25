package com.territorial.auction.domain.military.dto;

import java.time.LocalDateTime;

public record SiegeAlert(
        Long siegeId,
        String alertType, // "DECLARED" or "RESOLVED"
        Long territoryId,
        int coordX,
        int coordY,
        int attackZone,
        Long attackerId,
        String attackerNickname,
        Long defenderId,
        String defenderNickname,
        LocalDateTime resolveAt,
        Boolean isAttackerWin, // null when DECLARED
        String resultType // "LOOT" | "DEBUFF" | "AUCTION" | null
        ) {}
