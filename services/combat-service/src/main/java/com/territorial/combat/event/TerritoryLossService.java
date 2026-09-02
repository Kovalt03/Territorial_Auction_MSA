package com.territorial.combat.event;

import com.territorial.combat.domain.building.event.TerritoryLostEvent;
import com.territorial.combat.domain.building.service.GlobalVaultService;
import com.territorial.combat.domain.military.service.UnitRetreatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TerritoryLossService {

    private final GlobalVaultService globalVaultService;
    private final UnitRetreatService unitRetreatService;

    @Transactional
    public void handle(Long territoryId, Long formerOwnerId) {
        globalVaultService.handleTerritoryLost(new TerritoryLostEvent(territoryId, formerOwnerId));
        unitRetreatService.retreatFromLostTerritory(formerOwnerId, territoryId);
    }
}
