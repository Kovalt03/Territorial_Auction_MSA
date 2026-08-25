package com.territorial.auction.domain.military;

public final class ResearchPolicy {

    private ResearchPolicy() {}

    /** 목표 레벨 L로 올리는 연구 비용(공격자 금고 GP) = 이 값 × L */
    public static final int RESEARCH_COST_GP_PER_LEVEL = 2000;

    /** 연구 소요 시간(분) = 이 값 × L */
    public static final int RESEARCH_MINUTES_PER_LEVEL = 30;

    public static int costGp(int targetLevel) {
        return RESEARCH_COST_GP_PER_LEVEL * targetLevel;
    }

    public static int durationMinutes(int targetLevel) {
        return RESEARCH_MINUTES_PER_LEVEL * targetLevel;
    }

    /** 목표 레벨을 연구하려면 필요한 최소 연구소 레벨 = 목표 레벨 − 1 (L2→연구소1, L3→연구소2). */
    public static int requiredLabLevel(int targetLevel) {
        return targetLevel - 1;
    }
}
