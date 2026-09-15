package com.territorial.combat.domain.building.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.building.service.ProductionCreditService.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkshopSchedulerTest {

    @InjectMocks private WorkshopScheduler workshopScheduler;

    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private ProductionCreditService productionCreditService;

    @Nested
    @DisplayName("produceWorkshopGp")
    class ProduceWorkshopGp {

        @Test
        @DisplayName("영토 생산 → 위치별 적립 위임(GP)")
        void produceWorkshopGp_territory_delegatesCredit() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[] {10L, 200});
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(any()))
                    .willReturn(rows);
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(any()))
                    .willReturn(List.of());

            workshopScheduler.produceWorkshopGp();

            then(productionCreditService).should().creditTerritory(10L, 200, Resource.GP);
        }

        @Test
        @DisplayName("섬 생산 → 위치별 적립 위임(부스터 없으면 원액 그대로)")
        void produceWorkshopGp_island_delegatesCredit() {
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(any()))
                    .willReturn(List.of());
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[] {5L, 150});
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(any()))
                    .willReturn(rows);
            given(homeIslandRepository.findById(5L)).willReturn(Optional.empty());

            workshopScheduler.produceWorkshopGp();

            then(productionCreditService).should().creditIsland(5L, 150, Resource.GP);
        }

        @Test
        @DisplayName("생산 위치 없음 → 적립 위임 없음")
        void produceWorkshopGp_noProduction() {
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(any()))
                    .willReturn(List.of());
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(any()))
                    .willReturn(List.of());

            workshopScheduler.produceWorkshopGp();

            then(productionCreditService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("한 위치 적립 실패해도 예외 전파 없이 계속")
        void produceWorkshopGp_creditFailure_isolated() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[] {10L, 200});
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(any()))
                    .willReturn(rows);
            given(buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(any()))
                    .willReturn(List.of());
            org.mockito.BDDMockito.willThrow(new RuntimeException("락 획득 실패"))
                    .given(productionCreditService)
                    .creditTerritory(10L, 200, Resource.GP);

            workshopScheduler.produceWorkshopGp(); // 예외 없이 종료
        }
    }
}
