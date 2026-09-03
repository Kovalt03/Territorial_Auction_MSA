package com.territorial.combat.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WorkshopSchedulerTest {

    @InjectMocks private WorkshopScheduler workshopScheduler;

    @Mock private BuildingInstanceRepository buildingInstanceRepository;

    @Mock
    private com.territorial.combat.domain.building.repository.HomeIslandRepository
            homeIslandRepository;

    // Lv2 STORAGE — 용량 10,000
    private BuildingInstance storage(int gp) {
        BuildingType bt =
                BuildingType.builder().name("STORAGE").width(1).height(1).maxHp(60).build();
        BuildingInstance b =
                BuildingInstance.builder().buildingType(bt).posX(0).posY(0).hp(60).zone(2).build();
        ReflectionTestUtils.setField(b, "level", 2);
        ReflectionTestUtils.setField(b, "storedGp", gp);
        return b;
    }

    @Nested
    @DisplayName("produceWorkshopGp")
    class ProduceWorkshopGp {

        @Test
        @DisplayName("영토 생산 → 해당 위치 저장소에 GP 적립")
        void produceWorkshopGp_territory_creditsStorage() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[] {10L, 200});
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(any()))
                    .willReturn(rows);
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(any()))
                    .willReturn(List.of());
            BuildingInstance storage = storage(0);
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storage));

            workshopScheduler.produceWorkshopGp();

            assertThat(storage.getStoredGp()).isEqualTo(200);
        }

        @Test
        @DisplayName("섬 생산 → 섬 저장소에 GP 적립")
        void produceWorkshopGp_island_creditsStorage() {
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(any()))
                    .willReturn(List.of());
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[] {5L, 150});
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(any()))
                    .willReturn(rows);
            BuildingInstance storage = storage(1000);
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(5L))
                    .willReturn(List.of(storage));

            workshopScheduler.produceWorkshopGp();

            assertThat(storage.getStoredGp()).isEqualTo(1150);
        }

        @Test
        @DisplayName("생산 위치 없음 → 저장소 조회 없음")
        void produceWorkshopGp_noProduction() {
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(any()))
                    .willReturn(List.of());
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(any()))
                    .willReturn(List.of());

            workshopScheduler.produceWorkshopGp();

            then(buildingInstanceRepository)
                    .should(never())
                    .findStorageBuildingsByTerritoryIdWithLock(any());
        }

        @Test
        @DisplayName("저장 공간 없는 위치 → 생산분 소멸, 예외 없음")
        void produceWorkshopGp_noStorage_dropped() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[] {10L, 200});
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(any()))
                    .willReturn(rows);
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(any()))
                    .willReturn(List.of());
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(eq(10L)))
                    .willReturn(List.of());

            workshopScheduler.produceWorkshopGp();
        }
    }
}
