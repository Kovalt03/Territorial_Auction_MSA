package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminAuditLogListResponse;
import com.territorial.auction.domain.admin.dto.AdminAuditLogResponse;
import com.territorial.auction.domain.admin.entity.AdminAuditLog;
import com.territorial.auction.domain.admin.repository.AdminAuditLogRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;

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
        List<Long> adminIds = logs.stream().map(AdminAuditLog::getAdminUserId).distinct().toList();
        return userRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
    }

    private String blankToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }
}
