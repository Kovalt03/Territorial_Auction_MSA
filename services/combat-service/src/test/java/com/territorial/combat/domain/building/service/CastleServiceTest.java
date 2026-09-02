package com.territorial.combat.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.port.TerritoryContextPort;
import com.territorial.combat.domain.building.port.TerritoryContextPort.TerritoryContext;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingTypeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CastleServiceTest {

    @InjectMocks private CastleService castleService;

    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private TerritoryContextPort territoryContextPort;

    private TerritoryContext territory() {
        return new TerritoryContext(1L, 10L, 10, 2, 4);
    }

    private BuildingType castleType() {
        BuildingType bt =
                BuildingType.builder()
                        .name("CASTLE")
                        .width(2)
                        .height(2)
                        .maxHp(1000)
                        .baseCostGp(500)
                        .zoneRestriction(1)
                        .build();
        return bt;
    }

    @Test
    @DisplayName("이미 성 있으면 스킵(idempotent)")
    void createInitialCastle_idempotent() {
        given(buildingInstanceRepository.existsCastleOnTerritory(1L)).willReturn(true);

        castleService.createInitialCastle(1L);

        verify(buildingInstanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("성 없으면 중앙에 성 생성 (hp=maxHp, zone 1)")
    void createInitialCastle_creates() {
        given(buildingInstanceRepository.existsCastleOnTerritory(1L)).willReturn(false);
        given(territoryContextPort.findById(1L)).willReturn(Optional.of(territory()));
        given(buildingTypeRepository.findByName("CASTLE")).willReturn(Optional.of(castleType()));

        castleService.createInitialCastle(1L);

        ArgumentCaptor<BuildingInstance> captor = ArgumentCaptor.forClass(BuildingInstance.class);
        verify(buildingInstanceRepository).save(captor.capture());
        BuildingInstance saved = captor.getValue();
        assertThat(saved.getHp()).isEqualTo(1000);
        assertThat(saved.getZone()).isEqualTo(1);
        assertThat(saved.getPosX()).isEqualTo(4); // gridSize(10)/2 - 1
    }

    @Test
    @DisplayName("없는 영토 → TERRITORY_NOT_FOUND")
    void createInitialCastle_territoryNotFound() {
        given(buildingInstanceRepository.existsCastleOnTerritory(1L)).willReturn(false);
        given(territoryContextPort.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> castleService.createInitialCastle(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
    }

    @Test
    @DisplayName("CASTLE 타입 없음 → BUILDING_TYPE_NOT_FOUND")
    void createInitialCastle_typeNotFound() {
        given(buildingInstanceRepository.existsCastleOnTerritory(1L)).willReturn(false);
        given(territoryContextPort.findById(1L)).willReturn(Optional.of(territory()));
        given(buildingTypeRepository.findByName("CASTLE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> castleService.createInitialCastle(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUILDING_TYPE_NOT_FOUND);
    }
}
