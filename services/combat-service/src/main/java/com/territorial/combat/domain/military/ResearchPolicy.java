package com.territorial.combat.domain.military;

public final class ResearchPolicy {

    private ResearchPolicy() {}

    public static final int RESEARCH_COST_GP_PER_LEVEL = 2000;
    public static final int RESEARCH_MINUTES_PER_LEVEL = 30;

    public static int costGp(int targetLevel) {
        return RESEARCH_COST_GP_PER_LEVEL * targetLevel;
    }

    public static int durationMinutes(int targetLevel) {
        return RESEARCH_MINUTES_PER_LEVEL * targetLevel;
    }

    public static int requiredLabLevel(int targetLevel) {
        return targetLevel - 1;
    }
}
