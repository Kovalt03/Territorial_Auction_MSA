package com.territorial.admin.domain.admin.dto;

import java.util.List;

public record AdminAuctionListResponse(
        long totalCount, int page, int size, List<AdminAuctionResponse> auctions) {}
