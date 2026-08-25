package com.territorial.auction.domain.military;

public final class MilitaryPolicy {

    private MilitaryPolicy() {}

    public static final int SIEGE_COUNTDOWN_MINUTES = 30;
    public static final int ATTACK_COOLDOWN_HOURS = 2;
    public static final double ATTACKER_LOSS_RATE = 0.3;
    public static final double ATTACKER_FAIL_LOSS_RATE = 0.5;
    public static final double DEFENDER_LOSS_RATE = 0.3;

    /** Zone 3 약탈률 (STORAGE storedGp의 50%) */
    public static final double LOOT_RATE = 0.5;

    /** 건물 HP가 이 비율 이하일 때 Zone 클리어 판정 */
    public static final double ZONE_CLEAR_THRESHOLD = 0.5;

    /** 최외곽 Zone 번호. 공략은 이 Zone부터 중심(1)으로 진행한다. */
    public static final int OUTERMOST_ZONE = 3;

    /** WORKSHOP 파괴 후 생산 중단 시간 (시간 단위) */
    public static final int WORKSHOP_DEBUFF_HOURS = 12;

    // ── 건물별 주둔 수용량(레벨당) — 위치 총 슬롯 = 방어 가능 건물들의 이 값 합 ──────────────
    public static final int GARRISON_CAP_CASTLE = 5;
    public static final int GARRISON_CAP_RESIDENCE = 5;
    public static final int GARRISON_CAP_TOWER = 3;
    public static final int GARRISON_CAP_WALL = 2;

    /** CASTLE이 없을 때 기본 유닛 슬롯 */
    public static final int DEFAULT_UNIT_SLOTS = 5;

    /** CASTLE 레벨별 유닛 슬롯 수 (index = level) */
    private static final int[] CASTLE_UNIT_SLOTS = {0, 5, 10, 15};

    public static int castleUnitSlots(int level) {
        if (level < 1 || level >= CASTLE_UNIT_SLOTS.length) {
            return DEFAULT_UNIT_SLOTS;
        }
        return CASTLE_UNIT_SLOTS[level];
    }

    /** 유닛 1기당 위치 간 이동 비용(GP) — 출발지 저장소에서 차감 */
    public static final int UNIT_MOVE_COST_GP = 10;

    /** 유닛 위치 간 이동 소요 시간(분) — 도착 전까지 방어·배치·재이동 불가 */
    public static final int UNIT_MOVE_MINUTES = 10;

    // ── 공성 건물 ─────────────────────────────────────────────────────────────
    /** 공성당 지을 수 있는 공성 건물 총 개수 상한 */
    public static final int SIEGE_STRUCTURE_MAX = 8;

    /** 공성 건물은 대상 영토로부터 이 Chebyshev 거리 이내(=인접 타일)에만 배치 가능 */
    public static final int SIEGE_STRUCTURE_RANGE = 1;

    /** 맵 그리드 한 변 크기 — 좌표는 [0, 이 값) */
    public static final int MAP_GRID_SIZE = 50;

    /** 주둔지 1개당 제공하는 공격 병력 수용량. 공격 병력 상한 = 주둔지 수 × 이 값 */
    public static final int STAGING_CAPACITY_PER = 10;

    /** 공성 타워 1개당 공격력 버프(%) */
    public static final int SIEGE_TOWER_ATK_BONUS_PERCENT = 20;

    /** 공성 타워 버프가 누적 적용되는 최대 개수 */
    public static final int SIEGE_TOWER_MAX_EFFECTIVE = 3;

    /** 보급소 1개당 실패 후 공격 쿨다운 완화(시간) */
    public static final int SUPPLY_COOLDOWN_REDUCTION_HOURS = 1;

    /** 공성 건물 건설 비용(공격자 금고 GP) */
    public static final int STAGING_COST_GP = 500;

    public static final int SIEGE_TOWER_COST_GP = 800;
    public static final int SUPPLY_COST_GP = 600;

    public static int structureCostGp(
            com.territorial.auction.domain.military.entity.SiegeStructureType type) {
        return switch (type) {
            case STAGING -> STAGING_COST_GP;
            case TOWER -> SIEGE_TOWER_COST_GP;
            case SUPPLY -> SUPPLY_COST_GP;
        };
    }
}
