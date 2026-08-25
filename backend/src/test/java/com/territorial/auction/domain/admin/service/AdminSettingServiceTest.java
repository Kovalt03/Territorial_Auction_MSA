package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.dto.AdminBalanceSettingResponse;
import com.territorial.auction.domain.admin.entity.AdminSetting;
import com.territorial.auction.domain.admin.repository.AdminSettingRepository;
import com.territorial.auction.global.config.BalanceConfig;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
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
class AdminSettingServiceTest {

    @InjectMocks private AdminSettingService adminSettingService;
    @Mock private AdminSettingRepository adminSettingRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    @Nested
    @DisplayName("getBalanceSettings")
    class GetBalanceSettings {

        @Test
        @DisplayName("덮어쓴 값은 저장값, 나머지는 기본값으로 카탈로그 반환")
        void mergesStoredAndDefault() {
            given(adminSettingRepository.findBySettingKey(BalanceConfig.KEY_REPAIR_GP_PER_HP))
                    .willReturn(Optional.of(setting(BalanceConfig.KEY_REPAIR_GP_PER_HP, "5")));
            given(adminSettingRepository.findBySettingKey(BalanceConfig.KEY_GARRISON_CAP_CASTLE))
                    .willReturn(Optional.empty());
            given(adminSettingRepository.findBySettingKey(BalanceConfig.KEY_GARRISON_CAP_RESIDENCE))
                    .willReturn(Optional.empty());
            given(adminSettingRepository.findBySettingKey(BalanceConfig.KEY_GARRISON_CAP_TOWER))
                    .willReturn(Optional.empty());
            given(adminSettingRepository.findBySettingKey(BalanceConfig.KEY_GARRISON_CAP_WALL))
                    .willReturn(Optional.empty());

            List<AdminBalanceSettingResponse> result = adminSettingService.getBalanceSettings();

            AdminBalanceSettingResponse repair =
                    result.stream()
                            .filter(r -> r.key().equals(BalanceConfig.KEY_REPAIR_GP_PER_HP))
                            .findFirst()
                            .orElseThrow();
            assertThat(repair.value()).isEqualTo(5);
            assertThat(repair.defaultValue()).isEqualTo(2);
            AdminBalanceSettingResponse castle =
                    result.stream()
                            .filter(r -> r.key().equals(BalanceConfig.KEY_GARRISON_CAP_CASTLE))
                            .findFirst()
                            .orElseThrow();
            assertThat(castle.value()).isEqualTo(castle.defaultValue());
        }
    }

    @Nested
    @DisplayName("updateBalanceSetting")
    class UpdateBalanceSetting {

        @Test
        @DisplayName("카탈로그 키 → 저장 + 감사 로그")
        void success() {
            given(adminSettingRepository.findBySettingKey(BalanceConfig.KEY_GARRISON_CAP_TOWER))
                    .willReturn(Optional.empty());

            AdminBalanceSettingResponse response =
                    adminSettingService.updateBalanceSetting(
                            1L, BalanceConfig.KEY_GARRISON_CAP_TOWER, 7);

            assertThat(response.value()).isEqualTo(7);
            then(adminSettingRepository).should().save(any(AdminSetting.class));
            then(adminAuditLogger)
                    .should()
                    .record(eq(1L), eq("BALANCE_UPDATE"), eq("SETTING"), eq(null), any());
        }

        @Test
        @DisplayName("카탈로그에 없는 키 → BALANCE_KEY_NOT_FOUND")
        void unknownKey() {
            assertThatThrownBy(
                            () -> adminSettingService.updateBalanceSetting(1L, "UNKNOWN_KEY", 10))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BALANCE_KEY_NOT_FOUND);
        }
    }

    private AdminSetting setting(String key, String value) {
        return AdminSetting.builder().settingKey(key).settingValue(value).build();
    }
}
