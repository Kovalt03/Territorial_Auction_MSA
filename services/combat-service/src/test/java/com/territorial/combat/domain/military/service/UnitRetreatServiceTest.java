package com.territorial.combat.domain.military.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.combat.domain.building.entity.HomeIsland;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UnitRetreatServiceTest {

    @InjectMocks private UnitRetreatService service;
    @Mock private UnitInstanceRepository unitInstanceRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;

    private UnitType type;
    private HomeIsland island;

    @BeforeEach
    void setUp() {
        type =
                UnitType.builder()
                        .name("INFANTRY")
                        .attackPower(10)
                        .defensePower(10)
                        .costGp(100)
                        .foodCost(1)
                        .build();
        ReflectionTestUtils.setField(type, "id", 1L);
        island = HomeIsland.builder().userId(2L).build();
        ReflectionTestUtils.setField(island, "id", 20L);
    }

    @Test
    @DisplayName("파괴 건물의 병력은 홈 아일랜드 여유 수용량만큼 퇴각한다")
    void retreatWithinCapacity() {
        UnitInstance deployed =
                UnitInstance.builder().userId(2L).unitType(type).quantity(8).level(1).build();
        given(unitInstanceRepository.findByDeployedBuildingId(77L)).willReturn(List.of(deployed));
        given(homeIslandRepository.findByUserId(2L)).willReturn(Optional.of(island));
        given(buildingInstanceRepository.findCastleLevelByIslandId(20L)).willReturn(Optional.of(1));
        given(buildingInstanceRepository.sumResidenceCapacityByIslandId(eq(20L), anyTime()))
                .willReturn(0);
        given(unitInstanceRepository.sumQuantityByHomeIslandId(20L)).willReturn(2);
        given(unitInstanceRepository.findReadyIdleAtIsland(2L, 1L, 1, 20L))
                .willReturn(Optional.empty());

        service.retreatFromDestroyedBuilding(2L, 77L);

        org.mockito.ArgumentCaptor<UnitInstance> saved =
                org.mockito.ArgumentCaptor.forClass(UnitInstance.class);
        then(unitInstanceRepository).should().save(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(3);
        assertThat(saved.getValue().getHomeIsland()).isEqualTo(island);
        then(unitInstanceRepository).should().delete(deployed);
    }

    @Test
    @DisplayName("홈 아일랜드가 없으면 퇴각 대상 병력은 소멸한다")
    void retreatWithoutIslandDeletesUnits() {
        UnitInstance deployed =
                UnitInstance.builder().userId(2L).unitType(type).quantity(3).build();
        given(unitInstanceRepository.findByOwnerAndTerritoryAssociation(2L, 10L))
                .willReturn(List.of(deployed));
        given(homeIslandRepository.findByUserId(2L)).willReturn(Optional.empty());

        service.retreatFromLostTerritory(2L, 10L);

        then(unitInstanceRepository).should().deleteAll(List.of(deployed));
    }

    private java.time.LocalDateTime anyTime() {
        return org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class);
    }
}
