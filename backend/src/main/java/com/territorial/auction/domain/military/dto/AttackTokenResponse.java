package com.territorial.auction.domain.military.dto;

import com.territorial.auction.domain.military.entity.AttackToken;

public record AttackTokenResponse(Integer normalCount, Integer precisionCount) {

    public static AttackTokenResponse from(AttackToken token) {
        return new AttackTokenResponse(token.getNormalCount(), token.getPrecisionCount());
    }

    public static AttackTokenResponse empty() {
        return new AttackTokenResponse(0, 0);
    }
}
