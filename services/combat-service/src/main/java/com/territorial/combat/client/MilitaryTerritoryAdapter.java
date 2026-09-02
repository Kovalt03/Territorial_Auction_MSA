package com.territorial.combat.client;

import com.territorial.combat.domain.military.port.MilitaryTerritoryPort;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MilitaryTerritoryAdapter implements MilitaryTerritoryPort {

    private final TerritoryClient territoryClient;

    @Override
    public Optional<TerritoryLocation> findById(Long territoryId) {
        return territoryClient.findById(territoryId).map(this::toLocation);
    }

    @Override
    public List<TerritoryLocation> findOwnedByUserId(Long userId) {
        return territoryClient.findOwnedByUserId(userId).stream().map(this::toLocation).toList();
    }

    private TerritoryLocation toLocation(TerritoryClient.TerritoryCombatContextResponse value) {
        return new TerritoryLocation(
                value.territoryId(), value.ownerId(), value.coordX(), value.coordY());
    }
}
