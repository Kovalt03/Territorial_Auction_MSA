package com.territorial.admin.domain.admin.repository;

import com.territorial.admin.domain.admin.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    // 관리자 감사 로그 검색: action / targetType 필터(둘 다 nullable)
    @Query(
            "SELECT a FROM AdminAuditLog a "
                    + "WHERE (:action IS NULL OR a.action = :action) "
                    + "AND (:targetType IS NULL OR a.targetType = :targetType)")
    Page<AdminAuditLog> searchForAdmin(
            @Param("action") String action,
            @Param("targetType") String targetType,
            Pageable pageable);
}
