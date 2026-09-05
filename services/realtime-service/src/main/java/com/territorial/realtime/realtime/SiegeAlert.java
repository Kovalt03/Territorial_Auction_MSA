package com.territorial.realtime.realtime;

import java.time.LocalDateTime;

/** /sub/user/{userId}/siege-alert WS 페이로드. 필드명은 프론트 SiegeAlert 계약과 일치. */
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
