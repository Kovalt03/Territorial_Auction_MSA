package com.territorial.auction.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.territorial.auction.domain.admin.entity.AdminSetting;
import com.territorial.auction.domain.admin.repository.AdminSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalanceConfigTest {

    @InjectMocks private BalanceConfig balanceConfig;
    @Mock private AdminSettingRepository adminSettingRepository;

    @Test
    @DisplayName("설정 없음 → 기본값 반환")
    void getInt_missing_returnsDefault() {
        given(adminSettingRepository.findBySettingKey("REPAIR_GP_PER_HP"))
                .willReturn(Optional.empty());

        assertThat(balanceConfig.getInt("REPAIR_GP_PER_HP", 2)).isEqualTo(2);
    }

    @Test
    @DisplayName("설정 있음 → 저장값 반환")
    void getInt_present_returnsStored() {
        given(adminSettingRepository.findBySettingKey("REPAIR_GP_PER_HP"))
                .willReturn(Optional.of(setting("REPAIR_GP_PER_HP", "5")));

        assertThat(balanceConfig.getInt("REPAIR_GP_PER_HP", 2)).isEqualTo(5);
    }

    @Test
    @DisplayName("저장값이 숫자가 아니면 → 기본값 폴백")
    void getInt_nonNumeric_returnsDefault() {
        given(adminSettingRepository.findBySettingKey("REPAIR_GP_PER_HP"))
                .willReturn(Optional.of(setting("REPAIR_GP_PER_HP", "abc")));

        assertThat(balanceConfig.getInt("REPAIR_GP_PER_HP", 2)).isEqualTo(2);
    }

    private AdminSetting setting(String key, String value) {
        return AdminSetting.builder().settingKey(key).settingValue(value).build();
    }
}
