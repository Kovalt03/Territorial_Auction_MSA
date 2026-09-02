package com.territorial.combat.client;

import com.territorial.combat.domain.military.port.SiegeTerritoryPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SiegeTerritoryAdapter implements SiegeTerritoryPort {

    private final TerritoryClient territoryClient;

    @Override
    public Optional<TerritoryCombatContext> findById(Long territoryId) {
        return territoryClient
                .findById(territoryId)
                .map(
                        value ->
                                new TerritoryCombatContext(
                                        value.territoryId(),
                                        value.ownerId(),
                                        value.coordX(),
                                        value.coordY(),
                                        "OCCUPIED".equals(value.status()),
                                        value.protectedUntil()));
    }
}
