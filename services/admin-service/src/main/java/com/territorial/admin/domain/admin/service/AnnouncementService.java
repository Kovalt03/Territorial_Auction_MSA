package com.territorial.admin.domain.admin.service;

import com.territorial.admin.domain.admin.dto.AdminUpdateAnnouncementRequest;
import com.territorial.admin.domain.admin.dto.AnnouncementResponse;
import com.territorial.admin.domain.admin.entity.AdminSetting;
import com.territorial.admin.domain.admin.repository.AdminSettingRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AdminSettingRepository adminSettingRepository;
    private final AdminAuditLogger adminAuditLogger;

    /** 공개용: 활성 상태가 아니면 메시지를 비워 반환(초안 노출 방지). */
    public AnnouncementResponse getPublicAnnouncement() {
        boolean active = readActive();
        return new AnnouncementResponse(active, active ? readMessage() : "");
    }

    /** 관리자용: 활성 여부와 무관하게 저장된 메시지를 그대로 반환(편집용). */
    public AnnouncementResponse getForAdmin() {
        return new AnnouncementResponse(readActive(), readMessage());
    }

    @Transactional
    public AnnouncementResponse update(Long adminUserId, AdminUpdateAnnouncementRequest request) {
        String message = request.message() != null ? request.message() : "";
        upsert(AdminSetting.KEY_ANNOUNCEMENT_ACTIVE, Boolean.toString(request.active()));
        upsert(AdminSetting.KEY_ANNOUNCEMENT_MESSAGE, message);

        adminAuditLogger.record(
                adminUserId,
                "ANNOUNCEMENT_UPDATE",
                "SETTING",
                null,
                Map.of("active", request.active(), "message", message));
        return new AnnouncementResponse(request.active(), message);
    }

    private boolean readActive() {
        return adminSettingRepository
                .findBySettingKey(AdminSetting.KEY_ANNOUNCEMENT_ACTIVE)
                .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                .orElse(false);
    }

    private String readMessage() {
        return adminSettingRepository
                .findBySettingKey(AdminSetting.KEY_ANNOUNCEMENT_MESSAGE)
                .map(AdminSetting::getSettingValue)
                .orElse("");
    }

    private void upsert(String key, String value) {
        adminSettingRepository
                .findBySettingKey(key)
                .ifPresentOrElse(
                        s -> s.updateValue(value),
                        () ->
                                adminSettingRepository.save(
                                        AdminSetting.builder()
                                                .settingKey(key)
                                                .settingValue(value)
                                                .build()));
    }
}
