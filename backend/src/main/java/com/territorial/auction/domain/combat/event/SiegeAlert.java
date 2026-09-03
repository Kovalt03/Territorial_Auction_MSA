package com.territorial.auction.domain.combat.event;

import java.time.LocalDateTime;

public record SiegeAlert(
        Long siegeId,
        String alertType,
        Long territoryId,
        int coordX,
        int coordY,
        int attackZone,
        Long attackerId,
        String attackerNickname,
        Long defenderId,
        String defenderNickname,
        LocalDateTime resolveAt,
        Boolean isAttackerWin,
        String resultType) {}
