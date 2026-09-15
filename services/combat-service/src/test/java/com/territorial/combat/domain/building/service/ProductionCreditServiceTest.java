package com.territorial.combat.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.service.ProductionCreditService.Resource;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductionCreditServiceTest {

    @InjectMocks private ProductionCreditService service;

    @Mock private BuildingInstanceRepository buildingInstanceRepository;

    // Lv2 STORAGE — 용량 10,000
    private BuildingInstance storage() {
        BuildingType bt =
                BuildingType.builder().name("STORAGE").width(1).height(1).maxHp(60).build();
        BuildingInstance b =
                BuildingInstance.builder().buildingType(bt).posX(0).posY(0).hp(60).zone(2).build();
        ReflectionTestUtils.setField(b, "level", 2);
        return b;
    }

    @Test
    @DisplayName("영토 GP 적립 — 저장소에 채움")
    void creditTerritory_gp_fillsStorage() {
        BuildingInstance s = storage();
        given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                .willReturn(List.of(s));

        service.creditTerritory(10L, 200, Resource.GP);

        assertThat(s.getStoredGp()).isEqualTo(200);
    }

    @Test
    @DisplayName("섬 식량 적립 — 저장소에 채움")
    void creditIsland_food_fillsStorage() {
        BuildingInstance s = storage();
        given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(5L))
                .willReturn(List.of(s));

        service.creditIsland(5L, 150, Resource.FOOD);

        assertThat(s.getStoredFood()).isEqualTo(150);
    }

    @Test
    @DisplayName("저장 공간 없는 위치 → 생산분 소멸, 예외 없음")
    void credit_noStorage_dropped() {
        given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(any()))
                .willReturn(List.of());

        service.creditTerritory(10L, 200, Resource.GP); // 예외 없이 종료
    }
}
