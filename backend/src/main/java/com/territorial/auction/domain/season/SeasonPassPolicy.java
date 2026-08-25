package com.territorial.auction.domain.season;

public final class SeasonPassPolicy {

    private SeasonPassPolicy() {}

    public static final int MAX_LEVEL = 30;
    public static final int XP_PER_LEVEL = 1000;
    public static final int XP_AUCTION_WIN = 100;
    public static final int XP_SIEGE_VICTORY = 50;
    public static final int LEVEL_UP_COST_AP = 500;

    /** 건설 시간 감소 상한 — 패스 기본값 + 레벨 보상 누적의 합에 적용 */
    public static final int MAX_BUILD_TIME_REDUCTION_PCT = 50;
}
