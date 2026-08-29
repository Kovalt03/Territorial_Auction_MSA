package com.territorial.auction.event;

import java.time.LocalDateTime;

/** 경매 생성 시 발행. map 읽기 프로젝션(territory_auction_status)이 구독해 '경매중' 상태를 upsert한다. */
public record AuctionOpenedEvent(
        Long auctionId, Long territoryId, int currentPrice, LocalDateTime endAt) {}
