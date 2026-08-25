package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.admin.dto.AdminCreateBuildingTypeRequest;
import com.territorial.auction.domain.admin.dto.AdminUpdateBuildingTypeRequest;
import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse.BuildingTypeInfo;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBuildingServiceTest {

    @InjectMocks private AdminBuildingService adminBuildingService;

    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;

    @Mock
    private com.territorial.auction.domain.building.repository.BuildingLevelSpecRepository
            buildingLevelSpecRepository;

    @Mock private AdminAuditLogger adminAuditLogger;

    private BuildingType type(long id, String name) {
        BuildingType t =
                BuildingType.builder()
                        .name(name)
                        .width(2)
                        .height(2)
                        .maxHp(100)
                        .baseCostGp(1000)
                        .build();
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    @Test
    @DisplayName("ê±´ë¬¼ ìì± ì±ê³µ â ì´ë¦ ëë¬¸ì ì ì¥ + ê°ì¬ ë¡ê·¸")
    void create_success() {
        given(buildingTypeRepository.existsByName("LIGHTHOUSE")).willReturn(false);
        given(buildingTypeRepository.save(any()))
                .willAnswer(
                        inv -> {
                            BuildingType t = inv.getArgument(0);
                            ReflectionTestUtils.setField(t, "id", 9L);
                            return t;
                        });

        BuildingTypeInfo res =
                adminBuildingService.create(
                        10L,
                        new AdminCreateBuildingTypeRequest(
                                "lighthouse",
                                null,
                                1,
                                1,
                                50,
                                500,
                                null,
                                null,
                                null,
                                40,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "🗼",
                                "#44aaff"));

        assertThat(res.name()).isEqualTo("LIGHTHOUSE");
        assertThat(res.defensePower()).isEqualTo(40);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("BUILDING_TYPE_CREATE"), any(), any(), any());
    }

    @Test
    @DisplayName("ì¤ë³µ ì´ë¦ ìì± â DUPLICATE_BUILDING_TYPE_NAME")
    void create_duplicate() {
        given(buildingTypeRepository.existsByName("CASTLE")).willReturn(true);

        assertThatThrownBy(
                        () ->
                                adminBuildingService.create(
                                        10L,
                                        new AdminCreateBuildingTypeRequest(
                                                "castle", null, 2, 2, 100, 1000, null, null, null,
                                                null, null, null, null, null, null, null, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_BUILDING_TYPE_NAME);
        then(buildingTypeRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("기능 건물 코드로 생성 시도 → FUNCTIONAL_BUILDING_NOT_CREATABLE")
    void create_functionalRejected() {
        given(buildingTypeRepository.existsByName("WORKSHOP")).willReturn(false);

        assertThatThrownBy(
                        () ->
                                adminBuildingService.create(
                                        10L,
                                        new AdminCreateBuildingTypeRequest(
                                                "workshop",
                                                "생산소",
                                                2,
                                                1,
                                                100,
                                                1000,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                50,
                                                null,
                                                null,
                                                null,
                                                null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FUNCTIONAL_BUILDING_NOT_CREATABLE);
        then(buildingTypeRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("장식 건물 생성 → 생산 필드 무효화(null)")
    void create_decorativeNullsProduction() {
        given(buildingTypeRepository.existsByName("STATUE")).willReturn(false);
        given(buildingTypeRepository.save(any()))
                .willAnswer(
                        inv -> {
                            BuildingType t = inv.getArgument(0);
                            ReflectionTestUtils.setField(t, "id", 11L);
                            return t;
                        });

        BuildingTypeInfo res =
                adminBuildingService.create(
                        10L,
                        new AdminCreateBuildingTypeRequest(
                                "statue", "동상", 1, 1, 50, 300, null, null, null, 15, 99, 99, 99,
                                null, null, "🗽", "#cccccc"));

        assertThat(res.category()).isEqualTo("DECORATIVE");
        assertThat(res.defensePower()).isEqualTo(15);
        assertThat(res.gpProductionRate()).isNull();
        assertThat(res.foodProductionRate()).isNull();
        assertThat(res.unitCapacityPerLevel()).isNull();
    }

    @Test
    @DisplayName("ê±´ë¬¼ ìì  â ì¤í¯ ë°ì")
    void update_success() {
        BuildingType t = type(3L, "WORKSHOP");
        given(buildingTypeRepository.findById(3L)).willReturn(Optional.of(t));

        BuildingTypeInfo res =
                adminBuildingService.update(
                        10L,
                        3L,
                        new AdminUpdateBuildingTypeRequest(
                                null, 2, 1, 200, 2000, 500, null, null, null, null, null, 80, null,
                                null, null, null));

        assertThat(res.maxHp()).isEqualTo(200);
        assertThat(res.gpProductionRate()).isEqualTo(80);
        assertThat(res.upgradeCostGp()).isEqualTo(500);
    }

    @Test
    @DisplayName("레벨 스펙 설정 → 미존재 레벨 신규 저장 + 감사 로그")
    void updateLevelSpecs_savesNew() {
        BuildingType t = type(3L, "WORKSHOP");
        given(buildingTypeRepository.findById(3L)).willReturn(Optional.of(t));
        given(buildingLevelSpecRepository.findByBuildingType_IdAndLevel(3L, 2))
                .willReturn(Optional.empty());
        given(buildingLevelSpecRepository.findAllByBuildingType_Id(3L))
                .willReturn(java.util.List.of());

        adminBuildingService.updateLevelSpecs(
                10L,
                3L,
                java.util.Map.of(
                        2,
                        new com.territorial.auction.domain.admin.dto.AdminLevelSpecsRequest
                                .LevelSpecValues(1500, null, null, null, null, 40, null)));

        then(buildingLevelSpecRepository).should().save(any());
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("BUILDING_LEVEL_SPEC_UPDATE"), any(), any(), any());
    }

    @Test
    @DisplayName("허용 범위 밖 레벨 → INVALID_BUILDING_LEVEL")
    void updateLevelSpecs_invalidLevel() {
        BuildingType t = type(3L, "WORKSHOP");
        given(buildingTypeRepository.findById(3L)).willReturn(Optional.of(t));

        assertThatThrownBy(
                        () ->
                                adminBuildingService.updateLevelSpecs(
                                        10L,
                                        3L,
                                        java.util.Map.of(
                                                9,
                                                new com.territorial.auction.domain.admin.dto
                                                        .AdminLevelSpecsRequest.LevelSpecValues(
                                                        100, null, null, null, null, null, null))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_BUILDING_LEVEL);
    }

    @Test
    @DisplayName("배치된 건물 있으면 삭제 거부")
    void delete_inUse() {
        BuildingType t = type(3L, "WORKSHOP");
        given(buildingTypeRepository.findById(3L)).willReturn(Optional.of(t));
        given(buildingInstanceRepository.countByBuildingType_Id(3L)).willReturn(5L);

        assertThatThrownBy(() -> adminBuildingService.delete(10L, 3L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUILDING_TYPE_IN_USE);
        then(buildingTypeRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("ë¯¸ì¬ì© ê±´ë¬¼ ì­ì  ì±ê³µ")
    void delete_success() {
        BuildingType t = type(3L, "OLD_BUILDING");
        given(buildingTypeRepository.findById(3L)).willReturn(Optional.of(t));
        given(buildingInstanceRepository.countByBuildingType_Id(3L)).willReturn(0L);

        adminBuildingService.delete(10L, 3L);

        then(buildingTypeRepository).should().delete(t);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("BUILDING_TYPE_DELETE"), any(), any(), any());
    }
}
