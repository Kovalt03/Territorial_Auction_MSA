package com.territorial.admin.domain.admin.dto;

import java.util.List;

public record AdminAuditLogListResponse(
        long totalCount, int page, int size, List<AdminAuditLogResponse> logs) {}
