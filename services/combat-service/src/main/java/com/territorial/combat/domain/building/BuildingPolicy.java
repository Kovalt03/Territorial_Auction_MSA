package com.territorial.combat.domain.building;

public final class BuildingPolicy {

    private BuildingPolicy() {}

    public static final int MAX_LEVEL = 3;

    /** 섬 GP 수확 최대 누적 시간 (분 단위) — 24시간 초과분은 소멸 */
    public static final long MAX_HARVEST_ACCUMULATION_MINUTES = 24 * 60L;

    /** 건물 HP 1 회복당 GP 비용 — 위치 저장소에서 차감. 공성 중 방어 유지 비용. */
    public static final int REPAIR_GP_PER_HP = 2;

    /** 건물 HP 1 회복당 수리 소요 시간(초) — 수리는 즉시 완료가 없고 시간이 걸린다. */
    public static final int REPAIR_SECONDS_PER_HP = 3;

    /** AP 즉시 완료 단가 — 남은 시간 1분당 AP. 비용 = 올림(남은초÷60) × 단가. */
    public static final int RUSH_AP_PER_MINUTE = 10;

    /** 생산 부스터: 발동 시 지속 시간(시간)·배율·AP 비용. GP·식량 생산에 적용. */
    public static final long PRODUCTION_BOOST_DURATION_HOURS = 6L;

    public static final int PRODUCTION_BOOST_MULTIPLIER = 2;

    public static final int PRODUCTION_BOOST_AP_COST = 500;

    /** 남은 건설 시간(초)에 대한 AP 즉시 완료 비용. */
    public static int rushApCost(long remainingSeconds) {
        long minutes = (remainingSeconds + 59) / 60; // 올림
        return (int) minutes * RUSH_AP_PER_MINUTE;
    }

    /**
     * 부스터 배율이 반영된 유효 생산 분(minute). [from, to] 구간 중 부스터 구간과 겹치는 만큼 (배율-1)배 가중한다. 수확형(누적) 생산의 배율 적용에
     * 사용. 스케줄러형(정기 적립)은 발동 여부만 보고 amount 에 배율을 곱한다.
     */
    public static long boostWeightedMinutes(
            java.time.LocalDateTime from,
            java.time.LocalDateTime to,
            java.time.LocalDateTime boostUntil) {
        long base = java.time.temporal.ChronoUnit.MINUTES.between(from, to);
        if (base <= 0) {
            return 0;
        }
        if (boostUntil == null) {
            return base;
        }
        java.time.LocalDateTime boostStart = boostUntil.minusHours(PRODUCTION_BOOST_DURATION_HOURS);
        java.time.LocalDateTime lo = from.isAfter(boostStart) ? from : boostStart;
        java.time.LocalDateTime hi = to.isBefore(boostUntil) ? to : boostUntil;
        long overlap = lo.isBefore(hi) ? java.time.temporal.ChronoUnit.MINUTES.between(lo, hi) : 0;
        return base + (long) (PRODUCTION_BOOST_MULTIPLIER - 1) * overlap;
    }

    /** 레벨별 최대 HP = baseMaxHp × level */
    public static int scaledMaxHp(int baseMaxHp, int level) {
        return baseMaxHp * level;
    }

    /** 레벨업 비용 = baseCostGp × 현재 레벨 */
    public static int upgradeCost(int baseCostGp, int currentLevel) {
        return baseCostGp * currentLevel;
    }
}
