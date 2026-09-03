package com.territorial.combat.domain.military.dto;

import java.util.List;

public record SiegeTargetResponse(
        Long territoryId,
        int coordX,
        int coordY,
        List<ZoneHp> zones,
        List<TargetBuilding> buildings) {

    public record ZoneHp(int zone, int currentHp, int maxHp, int buildingCount) {}

    public record TargetBuilding(
            Long buildingId,
            String name,
            String displayName,
            int zone,
            int currentHp,
            int maxHp,
            int posX,
            int posY,
            int width,
            int height,
            boolean isUnderConstruction) {}
}
