package com.territorial.auction.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CastleServiceTest {

    @InjectMocks private CastleService castleService;

    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private TerritoryRepository territoryRepository;

    private Territory territory() {
        TerritoryGrade g =
                TerritoryGrade.builder()
                        .grade("A")
                        .productionMultiplier(BigDecimal.ONE)
                        .auctionPriceMultiplier(BigDecimal.ONE)
                        .preBuiltCount(0)
                        .spawnRate(BigDecimal.ONE)
                        .gridSize(10)
                        .build();
        Territory t = Territory.builder().coordX(1).coordY(2).grade(g).build();
        ReflectionTestUtils.setField(t, "id", 1L);
        return t;
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
        ReflectionTestUtils.setField(bt, "id", 1L);
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
        given(territoryRepository.findById(1L)).willReturn(Optional.of(territory()));
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
        given(territoryRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> castleService.createInitialCastle(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
    }

    @Test
    @DisplayName("CASTLE 타입 없음 → BUILDING_TYPE_NOT_FOUND")
    void createInitialCastle_typeNotFound() {
        given(buildingInstanceRepository.existsCastleOnTerritory(1L)).willReturn(false);
        given(territoryRepository.findById(1L)).willReturn(Optional.of(territory()));
        given(buildingTypeRepository.findByName("CASTLE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> castleService.createInitialCastle(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUILDING_TYPE_NOT_FOUND);
    }
}
