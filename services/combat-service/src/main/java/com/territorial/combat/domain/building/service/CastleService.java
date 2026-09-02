package com.territorial.combat.domain.building.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.port.TerritoryContextPort;
import com.territorial.combat.domain.building.port.TerritoryContextPort.TerritoryContext;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingTypeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(TerritoryContextPort.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CastleService {

    private static final String CASTLE_TYPE = "CASTLE";

    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingTypeRepository buildingTypeRepository;
    private final TerritoryContextPort territoryContextPort;

    @Transactional
    public void createInitialCastle(Long territoryId) {
        if (buildingInstanceRepository.existsCastleOnTerritory(territoryId)) {
            return;
        }
        TerritoryContext territory = findTerritory(territoryId);
        BuildingType castleType = findCastleType();
        int center = (territory.gridSize() / 2) - 1;
        buildingInstanceRepository.save(
                BuildingInstance.builder()
                        .territoryId(territoryId)
                        .buildingType(castleType)
                        .posX(center)
                        .posY(center)
                        .hp(castleType.getMaxHp())
                        .zone(1)
                        .build());
    }

    private TerritoryContext findTerritory(Long territoryId) {
        return territoryContextPort
                .findById(territoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
    }

    private BuildingType findCastleType() {
        return buildingTypeRepository
                .findByName(CASTLE_TYPE)
                .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
    }
}
