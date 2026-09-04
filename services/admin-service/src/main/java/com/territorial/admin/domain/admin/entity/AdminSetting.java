package com.territorial.admin.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 관리자가 제어하는 전역 설정을 key-value로 보관한다. 공지(announcement)도 이 키-값으로 저장한다.
@Entity
@Table(name = "admin_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminSetting {

    public static final String KEY_AUCTION_ENABLED = "AUCTION_ENABLED";
    public static final String KEY_ANNOUNCEMENT_ACTIVE = "ANNOUNCEMENT_ACTIVE";
    public static final String KEY_ANNOUNCEMENT_MESSAGE = "ANNOUNCEMENT_MESSAGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 50)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String settingValue;

    @Builder
    public AdminSetting(String settingKey, String settingValue) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
    }

    public void updateValue(String settingValue) {
        this.settingValue = settingValue;
    }
}
