package com.territorial.auction.dto;

import com.territorial.auction.entity.AuctionStatus;
import java.time.LocalDateTime;
import java.util.List;

public record MyBidListResponse(long totalCount, int page, int size, List<MyBidItemDto> bids) {

    public record MyBidItemDto(
            Long auctionId,
            Long territoryId,
            Integer coordX,
            Integer coordY,
            Integer myBidAmount,
            Integer currentPrice,
            Boolean isHighestBidder,
            LocalDateTime endAt,
            AuctionStatus status,
            String grade,
            String continentName) {}
}
