package com.territorial.combat.domain.military.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.port.TerritoryContextPort;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.military.entity.UnitResearch;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.entity.UnitTypeLevelSpec;
import com.territorial.combat.domain.military.repository.UnitResearchRepository;
import com.territorial.combat.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.combat.domain.military.repository.UnitTypeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResearchServiceTest {

    @InjectMocks private ResearchService service;
    @Mock private UnitResearchRepository researchRepository;
    @Mock private UnitTypeRepository typeRepository;
    @Mock private UnitTypeLevelSpecRepository specRepository;
    @Mock private BuildingInstanceRepository buildingRepository;
    @Mock private GlobalVaultRepository vaultRepository;
    @Mock private TerritoryContextPort territoryPort;

    private UnitType unitType;

    @BeforeEach
    void setUp() {
        unitType =
                UnitType.builder()
                        .name("INFANTRY")
                        .attackPower(10)
                        .defensePower(10)
                        .costGp(100)
                        .foodCost(1)
                        .level(1)
                        .build();
        ReflectionTestUtils.setField(unitType, "id", 2L);
    }

    private UnitResearch research(int level) {
        return UnitResearch.builder().userId(1L).unitType(unitType).researchedLevel(level).build();
    }

    private UnitTypeLevelSpec spec(int level) {
        return UnitTypeLevelSpec.builder()
                .unitType(unitType)
                .level(level)
                .attackPower(13)
                .defensePower(13)
                .trainCostFood(50)
                .requiredBarracksLevel(2)
                .build();
    }

    private GlobalVault vault(int gp) {
        GlobalVault vault = GlobalVault.builder().userId(1L).build();
        ReflectionTestUtils.setField(vault, "storedGp", gp);
        return vault;
    }

    @Test
    @DisplayName("연구 시작은 금고를 먼저 잠그고 비용과 완료 시각을 반영한다")
    void startResearch_success() {
        given(typeRepository.findById(2L)).willReturn(Optional.of(unitType));
        given(vaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(5000)));
        given(researchRepository.findByUserIdAndUnitTypeId(1L, 2L)).willReturn(Optional.empty());
        given(researchRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(specRepository.findByUnitType_IdAndLevel(2L, 2)).willReturn(Optional.of(spec(2)));
        given(territoryPort.findOwnedTerritoryIds(1L)).willReturn(List.of(10L));
        given(buildingRepository.findMaxResearchLabLevelByUserId(1L, List.of(10L)))
                .willReturn(Optional.of(1));

        var response = service.startResearch(1L, 2L);

        assertThat(response.pendingLevel()).isEqualTo(2);
        assertThat(response.vaultGpRemaining()).isEqualTo(1000);
        assertThat(response.researchCompleteAt()).isNotNull();
        InOrder order = inOrder(vaultRepository, researchRepository);
        order.verify(vaultRepository).findByIdWithLock(1L);
        order.verify(researchRepository).findByUserId(1L);
    }

    @Test
    @DisplayName("다른 유닛 연구가 진행 중이면 RESEARCH_IN_PROGRESS")
    void startResearch_inProgress() {
        UnitResearch active = research(1);
        active.startResearch(2, LocalDateTime.now().plusHours(1));
        given(typeRepository.findById(2L)).willReturn(Optional.of(unitType));
        given(vaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(5000)));
        given(researchRepository.findByUserId(1L)).willReturn(List.of(active));

        assertThatThrownBy(() -> service.startResearch(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESEARCH_IN_PROGRESS);
    }

    @Test
    @DisplayName("연구소 레벨이 부족하면 RESEARCH_LAB_LEVEL_INSUFFICIENT")
    void startResearch_labInsufficient() {
        given(typeRepository.findById(2L)).willReturn(Optional.of(unitType));
        given(vaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(5000)));
        given(researchRepository.findByUserIdAndUnitTypeId(1L, 2L))
                .willReturn(Optional.of(research(1)));
        given(specRepository.findByUnitType_IdAndLevel(2L, 2)).willReturn(Optional.of(spec(2)));
        given(territoryPort.findOwnedTerritoryIds(1L)).willReturn(List.of());
        given(buildingRepository.findMaxResearchLabLevelByUserId(1L, List.of()))
                .willReturn(Optional.of(0));

        assertThatThrownBy(() -> service.startResearch(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESEARCH_LAB_LEVEL_INSUFFICIENT);
    }

    @Test
    @DisplayName("최대 레벨이면 RESEARCH_MAX_REACHED")
    void startResearch_maxReached() {
        given(typeRepository.findById(2L)).willReturn(Optional.of(unitType));
        given(vaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(5000)));
        given(researchRepository.findByUserIdAndUnitTypeId(1L, 2L))
                .willReturn(Optional.of(research(3)));

        assertThatThrownBy(() -> service.startResearch(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESEARCH_MAX_REACHED);
    }

    @Test
    @DisplayName("연구 조회는 완료 시각이 지난 연구를 지연 정산한다")
    void getResearch_appliesCompletion() {
        UnitResearch completed = research(1);
        completed.startResearch(2, LocalDateTime.now().minusMinutes(1));
        given(territoryPort.findOwnedTerritoryIds(1L)).willReturn(List.of(10L));
        given(buildingRepository.findMaxResearchLabLevelByUserId(1L, List.of(10L)))
                .willReturn(Optional.of(1));
        given(researchRepository.findByUserId(1L)).willReturn(List.of(completed));
        given(typeRepository.findAll()).willReturn(List.of(unitType));
        given(specRepository.findAllByUnitType_Id(2L)).willReturn(List.of(spec(2)));

        var response = service.getResearch(1L);

        assertThat(response.units().get(0).researchedLevel()).isEqualTo(2);
        assertThat(response.units().get(0).pendingLevel()).isNull();
    }
}
