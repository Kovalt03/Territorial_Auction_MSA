package com.territorial.auction.domain.military.dto;

import com.territorial.auction.domain.military.entity.SiegeEvent;
import com.territorial.auction.domain.military.entity.SiegeResult;
import java.time.LocalDateTime;

public record SiegeResultResponse(
        Long siegeId,
        Boolean isAttackerWin,
        Integer attackerUnitsLost,
        Integer defenderUnitsLost,
        Integer lootedGp,
        String resultType,
        LocalDateTime resolvedAt) {

    public static SiegeResultResponse of(SiegeEvent siege, SiegeResult result) {
        return new SiegeResultResponse(
                siege.getId(),
                result.getIsAttackerWin(),
                result.getAttackerUnitsLost(),
                result.getDefenderUnitsLost(),
                result.getLootedGp(),
                result.getResultType() != null ? result.getResultType().name() : null,
                siege.getResolveAt());
    }
}
