package com.territorial.auction.domain.map.dto;

import java.time.LocalDateTime;

public record TaxStatusResponse(
        int territoryCount,
        TaxBreakdown taxBreakdown,
        int seasonPassExemptBonus,
        int effectiveExemptCount,
        int finalDailyGP,
        LocalDateTime nextChargeAt) {

    public record TaxBreakdown(int exemptCount, int taxableCount, int dailyGP) {}
}
