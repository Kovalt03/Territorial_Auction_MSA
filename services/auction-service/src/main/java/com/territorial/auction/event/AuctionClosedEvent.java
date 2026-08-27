package com.territorial.auction.event;

/** 경매 종료(낙찰·무낙찰 공통) 시 발행. map 읽기 프로젝션이 구독해 '경매중' 상태를 제거한다. */
public record AuctionClosedEvent(Long auctionId, Long territoryId) {}
