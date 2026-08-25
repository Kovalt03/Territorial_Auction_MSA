package com.territorial.auction.domain.auction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuctionDetailResponse(
        Long auctionId,
        Long territoryId,
        Integer coordX,
        Integer coordY,
        String grade,
        Integer currentPrice,
        String currentBidderNickname,
        LocalDateTime startAt,
        LocalDateTime endAt,
        List<RecentBidDto> recentBids) {

    public record RecentBidDto(String bidderNickname, Integer price, LocalDateTime bidAt) {}
}
