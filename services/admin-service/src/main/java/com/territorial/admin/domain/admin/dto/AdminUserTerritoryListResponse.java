package com.territorial.admin.domain.admin.dto;

import java.util.List;

public record AdminUserTerritoryListResponse(
        long totalCount, int page, int size, List<AdminUserTerritoryResponse> territories) {}
