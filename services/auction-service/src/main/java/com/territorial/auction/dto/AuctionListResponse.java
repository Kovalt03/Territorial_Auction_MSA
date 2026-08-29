package com.territorial.auction.dto;

import com.territorial.auction.entity.AuctionStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AuctionListResponse(
        long totalCount, int page, int size, List<AuctionItemDto> auctions) {

    public record AuctionItemDto(
            Long auctionId,
            Long territoryId,
            Integer coordX,
            Integer coordY,
            String continentName,
            String grade,
            Integer currentPrice,
            String currentBidderNickname,
            LocalDateTime endAt,
            AuctionStatus status) {}
}
