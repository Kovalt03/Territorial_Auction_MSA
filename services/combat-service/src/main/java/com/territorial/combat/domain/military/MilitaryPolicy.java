package com.territorial.combat.domain.military;

public final class MilitaryPolicy {

    private MilitaryPolicy() {}

    public static final int GARRISON_CAP_CASTLE = 5;
    public static final int GARRISON_CAP_RESIDENCE = 5;
    public static final int GARRISON_CAP_TOWER = 3;
    public static final int GARRISON_CAP_WALL = 2;
    public static final int DEFAULT_UNIT_SLOTS = 5;
    private static final int[] CASTLE_UNIT_SLOTS = {0, 5, 10, 15};
    public static final int UNIT_MOVE_COST_GP = 10;
    public static final int UNIT_MOVE_MINUTES = 10;

    public static int castleUnitSlots(int level) {
        if (level < 1 || level >= CASTLE_UNIT_SLOTS.length) {
            return DEFAULT_UNIT_SLOTS;
        }
        return CASTLE_UNIT_SLOTS[level];
    }
}
