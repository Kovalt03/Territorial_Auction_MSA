package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.client.CombatAdminClient;
import com.territorial.auction.domain.admin.dto.AdminBuildingTypeCatalogResponse.BuildingTypeInfo;
import com.territorial.auction.domain.admin.dto.AdminCreateBuildingTypeRequest;
import com.territorial.auction.domain.admin.dto.AdminUpdateBuildingTypeRequest;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminBuildingServiceTest {

    @InjectMocks private AdminBuildingService adminBuildingService;

    @Mock private CombatAdminClient combatAdminClient;
    @Mock private AdminAuditLogger adminAuditLogger;

    private BuildingTypeInfo type(long id, String name, Integer defense, Integer gp) {
        return new BuildingTypeInfo(
                id,
                name,
                name,
                "DECORATIVE",
                1,
                1,
                50,
                500,
                null,
                null,
                null,
                defense,
                null,
                null,
                gp,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("ê±´ë¬¼ ìì± ì±ê³µ â ì´ë¦ ëë¬¸ì ì ì¥ + ê°ì¬ ë¡ê·¸")
    void create_success() {
        AdminCreateBuildingTypeRequest request =
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
                        "#44aaff");
        given(combatAdminClient.createBuildingType(request))
                .willReturn(type(9L, "LIGHTHOUSE", 40, null));

        BuildingTypeInfo res = adminBuildingService.create(10L, request);

        assertThat(res.name()).isEqualTo("LIGHTHOUSE");
        assertThat(res.defensePower()).isEqualTo(40);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("BUILDING_TYPE_CREATE"), any(), any(), any());
    }

    @Test
    @DisplayName("ì¤ë³µ ì´ë¦ ìì± â DUPLICATE_BUILDING_TYPE_NAME")
    void create_duplicate() {
        given(combatAdminClient.createBuildingType(any()))
                .willThrow(new CustomException(ErrorCode.DUPLICATE_BUILDING_TYPE_NAME));

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
        then(adminAuditLogger).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기능 건물 코드로 생성 시도 → FUNCTIONAL_BUILDING_NOT_CREATABLE")
    void create_functionalRejected() {
        given(combatAdminClient.createBuildingType(any()))
                .willThrow(new CustomException(ErrorCode.FUNCTIONAL_BUILDING_NOT_CREATABLE));

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
        then(adminAuditLogger).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("장식 건물 생성 → 생산 필드 무효화(null)")
    void create_decorativeNullsProduction() {
        AdminCreateBuildingTypeRequest request =
                new AdminCreateBuildingTypeRequest(
                        "statue", "동상", 1, 1, 50, 300, null, null, null, 15, 99, 99, 99, null, null,
                        "🗽", "#cccccc");
        given(combatAdminClient.createBuildingType(request))
                .willReturn(type(11L, "STATUE", 15, null));

        BuildingTypeInfo res = adminBuildingService.create(10L, request);

        assertThat(res.category()).isEqualTo("DECORATIVE");
        assertThat(res.defensePower()).isEqualTo(15);
        assertThat(res.gpProductionRate()).isNull();
        assertThat(res.foodProductionRate()).isNull();
        assertThat(res.unitCapacityPerLevel()).isNull();
    }

    @Test
    @DisplayName("ê±´ë¬¼ ìì  â ì¤í¯ ë°ì")
    void update_success() {
        AdminUpdateBuildingTypeRequest request =
                new AdminUpdateBuildingTypeRequest(
                        null, 2, 1, 200, 2000, 500, null, null, null, null, null, 80, null, null,
                        null, null);
        BuildingTypeInfo updated =
                new BuildingTypeInfo(
                        3L,
                        "WORKSHOP",
                        null,
                        "FUNCTIONAL",
                        2,
                        1,
                        200,
                        2000,
                        500,
                        null,
                        null,
                        null,
                        null,
                        null,
                        80,
                        null,
                        null,
                        null,
                        null);
        given(combatAdminClient.updateBuildingType(3L, request)).willReturn(updated);

        BuildingTypeInfo res = adminBuildingService.update(10L, 3L, request);

        assertThat(res.maxHp()).isEqualTo(200);
        assertThat(res.gpProductionRate()).isEqualTo(80);
        assertThat(res.upgradeCostGp()).isEqualTo(500);
    }

    @Test
    @DisplayName("레벨 스펙 설정 → 미존재 레벨 신규 저장 + 감사 로그")
    void updateLevelSpecs_savesNew() {
        java.util.Map<
                        Integer,
                        com.territorial.auction.domain.admin.dto.AdminLevelSpecsRequest
                                .LevelSpecValues>
                specs =
                        java.util.Map.of(
                                2,
                                new com.territorial.auction.domain.admin.dto.AdminLevelSpecsRequest
                                        .LevelSpecValues(1500, null, null, null, null, 40, null));
        given(combatAdminClient.updateBuildingLevelSpecs(3L, specs)).willReturn(java.util.Map.of());

        adminBuildingService.updateLevelSpecs(10L, 3L, specs);

        then(combatAdminClient).should().updateBuildingLevelSpecs(3L, specs);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("BUILDING_LEVEL_SPEC_UPDATE"), any(), any(), any());
    }

    @Test
    @DisplayName("허용 범위 밖 레벨 → INVALID_BUILDING_LEVEL")
    void updateLevelSpecs_invalidLevel() {
        given(combatAdminClient.updateBuildingLevelSpecs(eq(3L), any()))
                .willThrow(new CustomException(ErrorCode.INVALID_BUILDING_LEVEL));

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
        given(combatAdminClient.deleteBuildingType(3L))
                .willThrow(new CustomException(ErrorCode.BUILDING_TYPE_IN_USE));

        assertThatThrownBy(() -> adminBuildingService.delete(10L, 3L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUILDING_TYPE_IN_USE);
        then(adminAuditLogger).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ë¯¸ì¬ì© ê±´ë¬¼ ì­ì  ì±ê³µ")
    void delete_success() {
        given(combatAdminClient.deleteBuildingType(3L)).willReturn("OLD_BUILDING");

        adminBuildingService.delete(10L, 3L);

        then(combatAdminClient).should().deleteBuildingType(3L);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("BUILDING_TYPE_DELETE"), any(), any(), any());
    }
}
