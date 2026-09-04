package com.territorial.admin.domain.admin.service;

import com.territorial.admin.domain.admin.dto.AdminAuditLogListResponse;
import com.territorial.admin.domain.admin.dto.AdminAuditLogResponse;
import com.territorial.admin.domain.admin.entity.AdminAccount;
import com.territorial.admin.domain.admin.entity.AdminAuditLog;
import com.territorial.admin.domain.admin.repository.AdminAccountRepository;
import com.territorial.admin.domain.admin.repository.AdminAuditLogRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminAccountRepository adminAccountRepository;

    public AdminAuditLogListResponse getLogs(String action, String targetType, Pageable pageable) {
        String actionFilter = blankToNull(action);
        String targetFilter = blankToNull(targetType);
        Page<AdminAuditLog> page =
                adminAuditLogRepository.searchForAdmin(actionFilter, targetFilter, pageable);

        Map<Long, String> nicknameById = resolveNicknames(page.getContent());
        List<AdminAuditLogResponse> logs =
                page.getContent().stream()
                        .map(
                                log ->
                                        AdminAuditLogResponse.from(
                                                log, nicknameById.get(log.getAdminUserId())))
                        .toList();
        return new AdminAuditLogListResponse(
                page.getTotalElements(), page.getNumber(), page.getSize(), logs);
    }

    private Map<Long, String> resolveNicknames(List<AdminAuditLog> logs) {
        // adminUserId는 admin_accounts의 id다(게임 유저 아님). 표시명은 관리자 이메일.
        List<Long> adminIds = logs.stream().map(AdminAuditLog::getAdminUserId).distinct().toList();
        return adminAccountRepository.findAllById(adminIds).stream()
                .collect(
                        Collectors.toMap(AdminAccount::getId, AdminAccount::getEmail, (a, b) -> a));
    }

    private String blankToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }
}
