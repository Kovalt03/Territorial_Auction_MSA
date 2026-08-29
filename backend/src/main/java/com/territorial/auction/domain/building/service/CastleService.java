package com.territorial.auction.domain.building.service;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CastleService {

    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingTypeRepository buildingTypeRepository;
    private final TerritoryRepository territoryRepository;

    /** 낙찰 영토에 초기 성 배치. 이미 있으면 스킵(idempotent) — 재점유·재시도 안전. */
    @Transactional
    public void createInitialCastle(Long territoryId) {
        if (buildingInstanceRepository.existsCastleOnTerritory(territoryId)) {
            return;
        }
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        BuildingType castleType =
                buildingTypeRepository
                        .findByName("CASTLE")
                        .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
        int center = (territory.getGrade().getGridSize() / 2) - 1;
        buildingInstanceRepository.save(
                BuildingInstance.builder()
                        .territory(territory)
                        .buildingType(castleType)
                        .posX(center)
                        .posY(center)
                        .hp(castleType.getMaxHp())
                        .zone(1)
                        .build());
    }
}
