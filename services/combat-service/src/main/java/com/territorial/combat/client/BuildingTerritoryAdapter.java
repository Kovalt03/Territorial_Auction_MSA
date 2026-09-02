package com.territorial.combat.client;

import com.territorial.combat.domain.building.port.TerritoryContextPort;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BuildingTerritoryAdapter implements TerritoryContextPort {

    private final TerritoryClient territoryClient;

    @Override
    public Optional<TerritoryContext> findById(Long territoryId) {
        return territoryClient
                .findById(territoryId)
                .map(
                        value ->
                                new TerritoryContext(
                                        value.territoryId(),
                                        value.ownerId(),
                                        value.gridSize(),
                                        value.zone1Radius(),
                                        value.zone2Radius()));
    }

    @Override
    public List<Long> findOwnedTerritoryIds(Long userId) {
        return territoryClient.findOwnedByUserId(userId).stream()
                .map(TerritoryClient.TerritoryCombatContextResponse::territoryId)
                .toList();
    }
}
