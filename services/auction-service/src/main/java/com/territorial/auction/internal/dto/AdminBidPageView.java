package com.territorial.auction.internal.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자용 유저 입찰 이력 페이지. 모놀리식 AdminUserBidListResponse와 필드명 일치(JSON 브리지). */
public record AdminBidPageView(long totalCount, int page, int size, List<Item> bids) {

    public record Item(
            Long auctionId,
            Long territoryId,
            int coordX,
            int coordY,
            String continentName,
            String grade,
            int myBidPrice,
            int currentPrice,
            LocalDateTime bidAt,
            boolean ongoing) {}
}
