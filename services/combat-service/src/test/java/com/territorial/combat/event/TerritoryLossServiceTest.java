package com.territorial.combat.event;

import static org.mockito.BDDMockito.then;

import com.territorial.combat.domain.building.event.TerritoryLostEvent;
import com.territorial.combat.domain.building.service.GlobalVaultService;
import com.territorial.combat.domain.military.service.UnitRetreatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerritoryLossServiceTest {

    @InjectMocks private TerritoryLossService service;
    @Mock private GlobalVaultService globalVaultService;
    @Mock private UnitRetreatService unitRetreatService;

    @Test
    void drainsResourcesAndRetreatsUnitsTogether() {
        service.handle(10L, 2L);

        then(globalVaultService).should().handleTerritoryLost(new TerritoryLostEvent(10L, 2L));
        then(unitRetreatService).should().retreatFromLostTerritory(2L, 10L);
    }
}
