package com.territorial.map.domain.map;

public final class LandTaxPolicy {

    public static final int BASE_EXEMPT_COUNT = 3;
    public static final int SEASON_PASS_EXEMPT_BONUS = 2;
    public static final int EVICTION_REAUCTION_DELAY_HOURS = 1;
    public static final int GRACE_PERIOD_HOURS = 24;

    /**
     * taxableCount = territoryCount - effectiveExemptCount 1~3 → 50 GP, 4~7 → 150 GP, 8+ → 400 GP
     */
    public static int calculateDailyTax(int taxableCount) {
        if (taxableCount <= 0) return 0;
        if (taxableCount <= 3) return 50;
        if (taxableCount <= 7) return 150;
        return 400;
    }

    private LandTaxPolicy() {}
}
