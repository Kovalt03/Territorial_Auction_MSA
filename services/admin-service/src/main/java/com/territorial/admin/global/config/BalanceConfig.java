package com.territorial.admin.global.config;

import com.territorial.admin.domain.admin.repository.AdminSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 밸런스 스칼라 상수를 {@code admin_settings} 키-값에서 읽는다. 관리자가 값을 덮어쓰면 그 값을, 없으면 호출부가 넘긴 기본값(Policy 상수)을
 * 사용한다. 재배포 없이 밸런스를 튜닝하기 위한 조회 헬퍼.
 */
@Component
@RequiredArgsConstructor
public class BalanceConfig {

    public static final String KEY_REPAIR_GP_PER_HP = "REPAIR_GP_PER_HP";
    public static final String KEY_GARRISON_CAP_CASTLE = "GARRISON_CAP_CASTLE";
    public static final String KEY_GARRISON_CAP_RESIDENCE = "GARRISON_CAP_RESIDENCE";
    public static final String KEY_GARRISON_CAP_TOWER = "GARRISON_CAP_TOWER";
    public static final String KEY_GARRISON_CAP_WALL = "GARRISON_CAP_WALL";

    private final AdminSettingRepository adminSettingRepository;

    public int getInt(String key, int defaultValue) {
        return adminSettingRepository
                .findBySettingKey(key)
                .map(setting -> parseIntOrDefault(setting.getSettingValue(), defaultValue))
                .orElse(defaultValue);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
