package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.dto.AdminUnitLevelSpecsRequest.UnitLevelValues;
import com.territorial.auction.domain.admin.dto.AdminUnitTypeResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateUnitTypeRequest;
import com.territorial.auction.domain.military.entity.UnitType;
import com.territorial.auction.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.auction.domain.military.repository.UnitTypeRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUnitServiceTest {

    @InjectMocks private AdminUnitService adminUnitService;

    @Mock private UnitTypeRepository unitTypeRepository;
    @Mock private UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private UnitType infantry() {
        UnitType t =
                UnitType.builder()
                        .name("INFANTRY")
                        .displayName("보병")
                        .attackPower(10)
                        .defensePower(10)
                        .costGp(100)
                        .foodCost(1)
                        .level(1)
                        .build();
        ReflectionTestUtils.setField(t, "id", 1L);
        return t;
    }

    @Test
    @DisplayName("유닛 수정 → 값 반영 + 감사 로그, 이름은 불변")
    void update_success() {
        given(unitTypeRepository.findById(1L)).willReturn(Optional.of(infantry()));

        AdminUnitTypeResponse res =
                adminUnitService.update(
                        10L,
                        1L,
                        new AdminUpdateUnitTypeRequest(
                                "창병", "🗡", "#ffffff", 15, 12, 150, 2, 3, 1));

        assertThat(res.name()).isEqualTo("INFANTRY");
        assertThat(res.displayName()).isEqualTo("창병");
        assertThat(res.attackPower()).isEqualTo(15);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("UNIT_TYPE_UPDATE"), any(), any(), any());
    }

    @Test
    @DisplayName("훈련 스펙 신규 저장")
    void updateLevelSpecs_savesNew() {
        given(unitTypeRepository.findById(1L)).willReturn(Optional.of(infantry()));
        given(unitTypeLevelSpecRepository.findByUnitType_IdAndLevel(1L, 2))
                .willReturn(Optional.empty());
        given(unitTypeLevelSpecRepository.findAllByUnitType_Id(1L)).willReturn(List.of());

        adminUnitService.updateLevelSpecs(10L, 1L, Map.of(2, new UnitLevelValues(13, 13, 50, 2)));

        then(unitTypeLevelSpecRepository).should().save(any());
    }

    @Test
    @DisplayName("훈련 스펙 부분 입력 → INCOMPLETE_UNIT_LEVEL_SPEC")
    void updateLevelSpecs_incomplete() {
        given(unitTypeRepository.findById(1L)).willReturn(Optional.of(infantry()));
        given(unitTypeLevelSpecRepository.findByUnitType_IdAndLevel(1L, 2))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                adminUnitService.updateLevelSpecs(
                                        10L, 1L, Map.of(2, new UnitLevelValues(13, null, 50, 2))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INCOMPLETE_UNIT_LEVEL_SPEC);
    }

    @Test
    @DisplayName("허용 범위 밖 레벨 → INVALID_UNIT_LEVEL")
    void updateLevelSpecs_invalidLevel() {
        given(unitTypeRepository.findById(1L)).willReturn(Optional.of(infantry()));

        assertThatThrownBy(
                        () ->
                                adminUnitService.updateLevelSpecs(
                                        10L, 1L, Map.of(9, new UnitLevelValues(13, 13, 50, 2))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_UNIT_LEVEL);
    }
}
