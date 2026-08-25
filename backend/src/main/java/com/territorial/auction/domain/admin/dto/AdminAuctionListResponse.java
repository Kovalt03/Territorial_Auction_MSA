package com.territorial.auction.domain.admin.dto;

import java.util.List;

public record AdminAuctionListResponse(
        long totalCount, int page, int size, List<AdminAuctionResponse> auctions) {}
