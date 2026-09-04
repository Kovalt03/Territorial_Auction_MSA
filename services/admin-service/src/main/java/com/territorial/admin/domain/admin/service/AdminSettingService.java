package com.territorial.admin.domain.admin.service;

import com.territorial.admin.domain.admin.dto.AdminAuctionSettingResponse;
import com.territorial.admin.domain.admin.dto.AdminBalanceSettingResponse;
import com.territorial.admin.domain.admin.entity.AdminSetting;
import com.territorial.admin.domain.admin.repository.AdminSettingRepository;
import com.territorial.admin.global.config.BalanceConfig;
import com.territorial.admin.global.exception.ErrorCode;
import com.territorial.auction.global.exception.CustomException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSettingService {

    private static final int DEFAULT_REPAIR_GP_PER_HP = 2;
    private static final int DEFAULT_GARRISON_CAP_CASTLE = 5;
    private static final int DEFAULT_GARRISON_CAP_RESIDENCE = 5;
    private static final int DEFAULT_GARRISON_CAP_TOWER = 3;
    private static final int DEFAULT_GARRISON_CAP_WALL = 2;

    // 관리자가 튜닝할 수 있는 밸런스 상수 카탈로그. 기본값은 각 도메인 Policy 상수와 일치한다.
    private record BalanceItem(String key, int defaultValue, String label) {}

    private static final List<BalanceItem> BALANCE_CATALOG =
            List.of(
                    new BalanceItem(
                            BalanceConfig.KEY_REPAIR_GP_PER_HP,
                            DEFAULT_REPAIR_GP_PER_HP,
                            "건물 HP 1 회복당 GP"),
                    new BalanceItem(
                            BalanceConfig.KEY_GARRISON_CAP_CASTLE,
                            DEFAULT_GARRISON_CAP_CASTLE,
                            "성 주둔 수용량(레벨당)"),
                    new BalanceItem(
                            BalanceConfig.KEY_GARRISON_CAP_RESIDENCE,
                            DEFAULT_GARRISON_CAP_RESIDENCE,
                            "숙소 주둔 수용량(레벨당)"),
                    new BalanceItem(
                            BalanceConfig.KEY_GARRISON_CAP_TOWER,
                            DEFAULT_GARRISON_CAP_TOWER,
                            "타워 주둔 수용량(레벨당)"),
                    new BalanceItem(
                            BalanceConfig.KEY_GARRISON_CAP_WALL,
                            DEFAULT_GARRISON_CAP_WALL,
                            "방벽 주둔 수용량(레벨당)"));

    private final AdminSettingRepository adminSettingRepository;
    private final AdminAuditLogger adminAuditLogger;

    public AdminAuctionSettingResponse getAuctionSetting() {
        return new AdminAuctionSettingResponse(isAuctionEnabled());
    }

    @Transactional
    public AdminAuctionSettingResponse setAuctionEnabled(Long adminUserId, boolean enabled) {
        AdminSetting setting =
                adminSettingRepository
                        .findBySettingKey(AdminSetting.KEY_AUCTION_ENABLED)
                        .orElseGet(
                                () ->
                                        AdminSetting.builder()
                                                .settingKey(AdminSetting.KEY_AUCTION_ENABLED)
                                                .settingValue(Boolean.TRUE.toString())
                                                .build());
        setting.updateValue(Boolean.toString(enabled));
        adminSettingRepository.save(setting);

        adminAuditLogger.record(
                adminUserId, "GLOBAL_AUCTION_TOGGLE", "SETTING", null, Map.of("enabled", enabled));
        return new AdminAuctionSettingResponse(enabled);
    }

    public List<AdminBalanceSettingResponse> getBalanceSettings() {
        return BALANCE_CATALOG.stream()
                .map(
                        item ->
                                new AdminBalanceSettingResponse(
                                        item.key(),
                                        currentValue(item),
                                        item.defaultValue(),
                                        item.label()))
                .toList();
    }

    @Transactional
    public AdminBalanceSettingResponse updateBalanceSetting(
            Long adminUserId, String key, int value) {
        BalanceItem item = findBalanceItemOrThrow(key);
        AdminSetting setting =
                adminSettingRepository
                        .findBySettingKey(key)
                        .orElseGet(
                                () ->
                                        AdminSetting.builder()
                                                .settingKey(key)
                                                .settingValue(Integer.toString(value))
                                                .build());
        setting.updateValue(Integer.toString(value));
        adminSettingRepository.save(setting);

        adminAuditLogger.record(
                adminUserId, "BALANCE_UPDATE", "SETTING", null, Map.of("key", key, "value", value));
        return new AdminBalanceSettingResponse(
                item.key(), value, item.defaultValue(), item.label());
    }

    private int currentValue(BalanceItem item) {
        return adminSettingRepository
                .findBySettingKey(item.key())
                .map(s -> parseIntOrDefault(s.getSettingValue(), item.defaultValue()))
                .orElse(item.defaultValue());
    }

    private BalanceItem findBalanceItemOrThrow(String key) {
        return BALANCE_CATALOG.stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.BALANCE_KEY_NOT_FOUND));
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean isAuctionEnabled() {
        return adminSettingRepository
                .findBySettingKey(AdminSetting.KEY_AUCTION_ENABLED)
                .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                .orElse(true);
    }
}
