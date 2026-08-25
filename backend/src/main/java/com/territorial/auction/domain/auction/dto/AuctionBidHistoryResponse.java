package com.territorial.auction.domain.auction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuctionBidHistoryResponse(Long auctionId, List<BidDto> bids) {

    public record BidDto(Integer price, LocalDateTime bidAt, String bidderNickname) {}
}
