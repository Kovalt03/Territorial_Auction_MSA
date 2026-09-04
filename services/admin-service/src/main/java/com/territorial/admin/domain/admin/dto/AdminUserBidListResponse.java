package com.territorial.admin.domain.admin.dto;

import java.util.List;

public record AdminUserBidListResponse(
        long totalCount, int page, int size, List<AdminUserBidResponse> bids) {}
