package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.dto.AdminUpdateAnnouncementRequest;
import com.territorial.auction.domain.admin.dto.AnnouncementResponse;
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
class AnnouncementServiceTest {

    @InjectMocks private AnnouncementService announcementService;

    @Mock private AdminSettingRepository adminSettingRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private AdminSetting setting(String key, String value) {
        return AdminSetting.builder().settingKey(key).settingValue(value).build();
    }

    @Test
    @DisplayName("공개 조회 - 비활성이면 메시지를 비워 반환")
    void getPublic_inactiveHidesMessage() {
        given(adminSettingRepository.findBySettingKey(AdminSetting.KEY_ANNOUNCEMENT_ACTIVE))
                .willReturn(Optional.of(setting(AdminSetting.KEY_ANNOUNCEMENT_ACTIVE, "false")));

        AnnouncementResponse res = announcementService.getPublicAnnouncement();

        assertThat(res.active()).isFalse();
        assertThat(res.message()).isEmpty();
    }

    @Test
    @DisplayName("공개 조회 - 활성이면 메시지 노출")
    void getPublic_activeShowsMessage() {
        given(adminSettingRepository.findBySettingKey(AdminSetting.KEY_ANNOUNCEMENT_ACTIVE))
                .willReturn(Optional.of(setting(AdminSetting.KEY_ANNOUNCEMENT_ACTIVE, "true")));
        given(adminSettingRepository.findBySettingKey(AdminSetting.KEY_ANNOUNCEMENT_MESSAGE))
                .willReturn(Optional.of(setting(AdminSetting.KEY_ANNOUNCEMENT_MESSAGE, "점검 안내")));

        AnnouncementResponse res = announcementService.getPublicAnnouncement();

        assertThat(res.active()).isTrue();
        assertThat(res.message()).isEqualTo("점검 안내");
    }

    @Test
    @DisplayName("설정 없으면 기본 비활성")
    void getPublic_defaultInactive() {
        given(adminSettingRepository.findBySettingKey(AdminSetting.KEY_ANNOUNCEMENT_ACTIVE))
                .willReturn(Optional.empty());

        AnnouncementResponse res = announcementService.getPublicAnnouncement();

        assertThat(res.active()).isFalse();
    }

    @Test
    @DisplayName("업데이트 - 신규 키 저장 + 감사 로그")
    void update_savesAndAudits() {
        given(adminSettingRepository.findBySettingKey(any())).willReturn(Optional.empty());

        AnnouncementResponse res =
                announcementService.update(
                        10L, new AdminUpdateAnnouncementRequest(true, "이벤트 진행 중"));

        assertThat(res.active()).isTrue();
        assertThat(res.message()).isEqualTo("이벤트 진행 중");
        then(adminSettingRepository).should(org.mockito.Mockito.times(2)).save(any());
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("ANNOUNCEMENT_UPDATE"), eq("SETTING"), eq(null), any());
    }
}
