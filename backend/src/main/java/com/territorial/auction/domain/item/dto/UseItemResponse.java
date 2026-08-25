package com.territorial.auction.domain.item.dto;

import java.time.LocalDateTime;

public record UseItemResponse(Long itemId, String itemType, UseResult result, int remainingCount) {

    public record UseResult(
            Long territoryId,
            LocalDateTime invincibleUntil,
            Integer normalCount,
            Integer precisionCount) {

        public static UseResult ofInvincibility(Long territoryId, LocalDateTime until) {
            return new UseResult(territoryId, until, null, null);
        }

        public static UseResult ofAttackToken(int normalCount, int precisionCount) {
            return new UseResult(null, null, normalCount, precisionCount);
        }
    }
}
