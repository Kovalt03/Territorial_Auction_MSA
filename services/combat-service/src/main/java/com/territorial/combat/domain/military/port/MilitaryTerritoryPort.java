package com.territorial.combat.domain.military.port;

import java.util.List;
import java.util.Optional;

public interface MilitaryTerritoryPort {

    Optional<TerritoryLocation> findById(Long territoryId);

    List<TerritoryLocation> findOwnedByUserId(Long userId);

    record TerritoryLocation(Long territoryId, Long ownerId, int coordX, int coordY) {}
}
