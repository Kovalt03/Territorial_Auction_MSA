package com.territorial.combat.domain.military;

public final class MilitaryPolicy {

    private MilitaryPolicy() {}

    public static final int SIEGE_COUNTDOWN_MINUTES = 30;
    public static final int ATTACK_COOLDOWN_HOURS = 2;
    public static final double ATTACKER_LOSS_RATE = 0.3;
    public static final double ATTACKER_FAIL_LOSS_RATE = 0.5;
    public static final double DEFENDER_LOSS_RATE = 0.3;
    public static final double LOOT_RATE = 0.5;
    public static final double ZONE_CLEAR_THRESHOLD = 0.5;
    public static final int OUTERMOST_ZONE = 3;
    public static final int WORKSHOP_DEBUFF_HOURS = 12;
    public static final int GARRISON_CAP_CASTLE = 5;
    public static final int GARRISON_CAP_RESIDENCE = 5;
    public static final int GARRISON_CAP_TOWER = 3;
    public static final int GARRISON_CAP_WALL = 2;
    public static final int DEFAULT_UNIT_SLOTS = 5;
    private static final int[] CASTLE_UNIT_SLOTS = {0, 5, 10, 15};
    public static final int UNIT_MOVE_COST_GP = 10;
    public static final int UNIT_MOVE_MINUTES = 10;
    public static final int SIEGE_STRUCTURE_MAX = 8;
    public static final int SIEGE_STRUCTURE_RANGE = 1;
    public static final int MAP_GRID_SIZE = 50;
    public static final int STAGING_CAPACITY_PER = 10;
    public static final int SIEGE_TOWER_ATK_BONUS_PERCENT = 20;
    public static final int SIEGE_TOWER_MAX_EFFECTIVE = 3;
    public static final int SUPPLY_COOLDOWN_REDUCTION_HOURS = 1;
    public static final int STAGING_COST_GP = 500;
    public static final int SIEGE_TOWER_COST_GP = 800;
    public static final int SUPPLY_COST_GP = 600;

    public static int castleUnitSlots(int level) {
        if (level < 1 || level >= CASTLE_UNIT_SLOTS.length) {
            return DEFAULT_UNIT_SLOTS;
        }
        return CASTLE_UNIT_SLOTS[level];
    }

    public static int structureCostGp(
            com.territorial.combat.domain.military.entity.SiegeStructureType type) {
        return switch (type) {
            case STAGING -> STAGING_COST_GP;
            case TOWER -> SIEGE_TOWER_COST_GP;
            case SUPPLY -> SUPPLY_COST_GP;
        };
    }
}
