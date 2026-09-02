package com.territorial.combat.domain.military.dto;

import com.territorial.combat.domain.military.entity.AttackToken;

public record AttackTokenResponse(Integer normalCount, Integer precisionCount) {
    public static AttackTokenResponse from(AttackToken token) {
        return new AttackTokenResponse(token.getNormalCount(), token.getPrecisionCount());
    }

    public static AttackTokenResponse empty() {
        return new AttackTokenResponse(0, 0);
    }
}
