package com.territorial.auction.internal.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 진행 중 경매 목록. 모놀리식 AdminAuctionListResponse/AdminAuctionResponse와 필드명 일치(JSON 브리지). */
public record AdminAuctionListView(long totalCount, int page, int size, List<Item> auctions) {

    public record Item(
            Long auctionId,
            Long territoryId,
            int coordX,
            int coordY,
            String continentName,
            String grade,
            int currentPrice,
            Long currentBidderId,
            String currentBidderNickname,
            LocalDateTime endAt) {}
}
