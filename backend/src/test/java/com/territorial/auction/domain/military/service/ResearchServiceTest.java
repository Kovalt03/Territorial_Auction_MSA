package com.territorial.auction.domain.military.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.military.entity.UnitResearch;
import com.territorial.auction.domain.military.entity.UnitType;
import com.territorial.auction.domain.military.entity.UnitTypeLevelSpec;
import com.territorial.auction.domain.military.repository.UnitResearchRepository;
import com.territorial.auction.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.auction.domain.military.repository.UnitTypeRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
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

    @InjectMocks private ResearchService researchService;

    @Mock private UnitResearchRepository unitResearchRepository;
    @Mock private UnitTypeRepository unitTypeRepository;
    @Mock private UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private UserRepository userRepository;

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
        UnitResearch r = UnitResearch.builder().unitType(unitType).researchedLevel(level).build();
        return r;
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
        GlobalVault v = GlobalVault.builder().build();
        ReflectionTestUtils.setField(v, "storedGp", gp);
        return v;
    }

    @Test
    @DisplayName("연구 시작 성공 → 금고 차감 + 진행 레벨 설정")
    void startResearch_success() {
        given(unitTypeRepository.findById(2L)).willReturn(Optional.of(unitType));
        given(unitResearchRepository.findByUserIdAndUnitTypeId(1L, 2L))
                .willReturn(Optional.empty());
        given(userRepository.getReferenceById(1L)).willReturn(org.mockito.Mockito.mock(User.class));
        given(unitResearchRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(unitTypeLevelSpecRepository.findByUnitType_IdAndLevel(2L, 2))
                .willReturn(Optional.of(spec(2)));
        given(buildingInstanceRepository.findMaxResearchLabLevelByUserId(1L))
                .willReturn(Optional.of(1)); // 연구소 Lv1 → L2 연구 가능
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(5000)));

        var res = researchService.startResearch(1L, 2L);

        assertThat(res.pendingLevel()).isEqualTo(2);
        assertThat(res.vaultGpRemaining()).isEqualTo(5000 - 2000 * 2); // cost = 2000 × 2
        assertThat(res.researchCompleteAt()).isNotNull();
        InOrder lockOrder = inOrder(globalVaultRepository, unitResearchRepository);
        lockOrder.verify(globalVaultRepository).findByIdWithLock(1L);
        lockOrder.verify(unitResearchRepository).findByUserId(1L);
    }

    @Test
    @DisplayName("다른 유닛이 연구 중이면 → RESEARCH_IN_PROGRESS (한 번에 하나)")
    void startResearch_anotherInProgress() {
        given(unitTypeRepository.findById(2L)).willReturn(Optional.of(unitType));
        UnitResearch other = research(1);
        other.startResearch(2, java.time.LocalDateTime.now().plusHours(1)); // 진행 중
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(5000)));
        given(unitResearchRepository.findByUserId(1L)).willReturn(java.util.List.of(other));

        assertThatThrownBy(() -> researchService.startResearch(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESEARCH_IN_PROGRESS);
    }

    @Test
    @DisplayName("연구소 레벨 부족 → RESEARCH_LAB_LEVEL_INSUFFICIENT")
    void startResearch_labInsufficient() {
        given(unitTypeRepository.findById(2L)).willReturn(Optional.of(unitType));
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(5000)));
        given(unitResearchRepository.findByUserIdAndUnitTypeId(1L, 2L))
                .willReturn(Optional.of(research(1)));
        given(unitTypeLevelSpecRepository.findByUnitType_IdAndLevel(2L, 2))
                .willReturn(Optional.of(spec(2)));
        given(buildingInstanceRepository.findMaxResearchLabLevelByUserId(1L))
                .willReturn(Optional.of(0)); // 연구소 없음

        assertThatThrownBy(() -> researchService.startResearch(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESEARCH_LAB_LEVEL_INSUFFICIENT);
    }

    @Test
    @DisplayName("이미 최대 레벨 → RESEARCH_MAX_REACHED")
    void startResearch_maxReached() {
        given(unitTypeRepository.findById(2L)).willReturn(Optional.of(unitType));
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(5000)));
        given(unitResearchRepository.findByUserIdAndUnitTypeId(1L, 2L))
                .willReturn(Optional.of(research(3))); // MAX_LEVEL=3 → target 4 초과

        assertThatThrownBy(() -> researchService.startResearch(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESEARCH_MAX_REACHED);
    }
}
