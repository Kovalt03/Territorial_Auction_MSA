package com.territorial.auction.domain.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.admin.entity.AdminAuditLog;
import com.territorial.auction.domain.admin.repository.AdminAuditLogRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 관리자 쓰기 작업 감사 로그 기록. 호출 서비스의 트랜잭션에 참여한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditLogger {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;

    public void record(
            Long adminUserId,
            String action,
            String targetType,
            Long targetId,
            Map<String, Object> detail) {
        adminAuditLogRepository.save(
                AdminAuditLog.builder()
                        .adminUserId(adminUserId)
                        .action(action)
                        .targetType(targetType)
                        .targetId(targetId)
                        .detailJson(serialize(detail))
                        .build());
    }

    private String serialize(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            log.warn("감사 로그 detail 직렬화 실패", e);
            return String.valueOf(detail);
        }
    }
}
