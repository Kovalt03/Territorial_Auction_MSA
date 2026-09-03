package com.territorial.combat.domain.building.port;

import java.util.List;
import java.util.Optional;

public interface TerritoryContextPort {

    Optional<TerritoryContext> findById(Long territoryId);

    List<Long> findOwnedTerritoryIds(Long userId);

    record TerritoryContext(
            Long territoryId, Long ownerId, int gridSize, int zone1Radius, int zone2Radius) {}
}
