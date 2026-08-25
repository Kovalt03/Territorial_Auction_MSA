package com.territorial.auction.domain.ranking.event;

public record AuctionSettledEvent(Long userId, Long seasonId, int finalPrice) {}
