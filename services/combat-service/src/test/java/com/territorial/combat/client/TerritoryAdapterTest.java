package com.territorial.combat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.territorial.combat.client.TerritoryClient.TerritoryCombatContextResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerritoryAdapterTest {

    @Mock private TerritoryClient client;

    @Test
    void mapsOneContextToEachDomainPort() {
        LocalDateTime protectedUntil = LocalDateTime.now().plusHours(1);
        TerritoryCombatContextResponse context =
                new TerritoryCombatContextResponse(
                        10L, 2L, 4, 5, "OCCUPIED", protectedUntil, "A", 12, 2, 4);
        given(client.findById(10L)).willReturn(Optional.of(context));

        assertThat(new BuildingTerritoryAdapter(client).findById(10L).orElseThrow().gridSize())
                .isEqualTo(12);
        assertThat(new MilitaryTerritoryAdapter(client).findById(10L).orElseThrow().coordX())
                .isEqualTo(4);
        assertThat(new SiegeTerritoryAdapter(client).findById(10L).orElseThrow().occupied())
                .isTrue();
    }

    @Test
    void mapsOwnedContextsForBuildingAndMilitary() {
        TerritoryCombatContextResponse context =
                new TerritoryCombatContextResponse(10L, 2L, 4, 5, "OCCUPIED", null, "A", 12, 2, 4);
        given(client.findOwnedByUserId(2L)).willReturn(List.of(context));

        assertThat(new BuildingTerritoryAdapter(client).findOwnedTerritoryIds(2L))
                .containsExactly(10L);
        assertThat(new MilitaryTerritoryAdapter(client).findOwnedByUserId(2L))
                .extracting("territoryId")
                .containsExactly(10L);
    }
}
