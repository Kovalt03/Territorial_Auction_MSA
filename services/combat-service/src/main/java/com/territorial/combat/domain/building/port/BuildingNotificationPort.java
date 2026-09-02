package com.territorial.combat.domain.building.port;

public interface BuildingNotificationPort {

    void notifyIslandExpanded(Long userId, int storedBuildingCount);
}
