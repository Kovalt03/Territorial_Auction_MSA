package com.territorial.combat.domain.military.port;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SiegeTerritoryPort {

    Optional<TerritoryCombatContext> findById(Long territoryId);

    record TerritoryCombatContext(
            Long territoryId,
            Long ownerId,
            int coordX,
            int coordY,
            boolean occupied,
            LocalDateTime protectedUntil) {}
}
