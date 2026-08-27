package com.territorial.auction.internal.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자용 유저 진행 중 입찰 목록. 모놀리식 AdminUserActiveBidListResponse와 필드명 일치(JSON 브리지). */
public record AdminActiveBidListView(List<Item> activeBids) {

    public record Item(
            Long auctionId,
            Long territoryId,
            int coordX,
            int coordY,
            String continentName,
            String grade,
            int myBidPrice,
            int currentPrice,
            boolean topBidder,
            LocalDateTime endAt) {}
}
