package com.territorial.admin.domain.admin.dto;

import com.territorial.admin.domain.admin.entity.AdminAuditLog;
import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        Long id,
        Long adminUserId,
        String adminNickname,
        String action,
        String targetType,
        Long targetId,
        String detailJson,
        LocalDateTime createdAt) {

    public static AdminAuditLogResponse from(AdminAuditLog log, String adminNickname) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAdminUserId(),
                adminNickname,
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetailJson(),
                log.getCreatedAt());
    }
}
