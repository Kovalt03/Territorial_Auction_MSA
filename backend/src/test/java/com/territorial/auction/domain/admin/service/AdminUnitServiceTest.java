package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.client.CombatAdminClient;
import com.territorial.auction.domain.admin.dto.AdminUnitLevelSpecsRequest.UnitLevelValues;
import com.territorial.auction.domain.admin.dto.AdminUnitTypeResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateUnitTypeRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUnitServiceTest {

    @InjectMocks private AdminUnitService adminUnitService;

    @Mock private CombatAdminClient combatAdminClient;
    @Mock private AdminAuditLogger adminAuditLogger;

    private AdminUnitTypeResponse infantry(String displayName, int attackPower) {
        return new AdminUnitTypeResponse(
                1L, "INFANTRY", displayName, null, null, attackPower, 12, 150, 2, 3, 1);
    }

    @Test
    @DisplayName("유닛 수정 → 값 반영 + 감사 로그, 이름은 불변")
    void update_success() {
        AdminUpdateUnitTypeRequest request =
                new AdminUpdateUnitTypeRequest("창병", "🗡", "#ffffff", 15, 12, 150, 2, 3, 1);
        given(combatAdminClient.updateUnitType(1L, request)).willReturn(infantry("창병", 15));

        AdminUnitTypeResponse res = adminUnitService.update(10L, 1L, request);

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
        Map<Integer, UnitLevelValues> specs = Map.of(2, new UnitLevelValues(13, 13, 50, 2));
        given(combatAdminClient.updateUnitLevelSpecs(1L, specs)).willReturn(Map.of());

        adminUnitService.updateLevelSpecs(10L, 1L, specs);

        then(combatAdminClient).should().updateUnitLevelSpecs(1L, specs);
    }

    @Test
    @DisplayName("훈련 스펙 부분 입력 → INCOMPLETE_UNIT_LEVEL_SPEC")
    void updateLevelSpecs_incomplete() {
        Map<Integer, UnitLevelValues> specs = Map.of(2, new UnitLevelValues(13, null, 50, 2));
        given(combatAdminClient.updateUnitLevelSpecs(1L, specs)).willReturn(Map.of());

        adminUnitService.updateLevelSpecs(10L, 1L, specs);

        then(combatAdminClient).should().updateUnitLevelSpecs(1L, specs);
    }

    @Test
    @DisplayName("허용 범위 밖 레벨 → INVALID_UNIT_LEVEL")
    void updateLevelSpecs_invalidLevel() {
        Map<Integer, UnitLevelValues> specs = Map.of(9, new UnitLevelValues(13, 13, 50, 2));
        given(combatAdminClient.updateUnitLevelSpecs(1L, specs)).willReturn(Map.of());

        adminUnitService.updateLevelSpecs(10L, 1L, specs);

        then(combatAdminClient).should().updateUnitLevelSpecs(1L, specs);
    }
}
