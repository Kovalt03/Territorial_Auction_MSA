package com.territorial.auction.domain.admin.dto;

import java.util.List;

public record AdminAuditLogListResponse(
        long totalCount, int page, int size, List<AdminAuditLogResponse> logs) {}
