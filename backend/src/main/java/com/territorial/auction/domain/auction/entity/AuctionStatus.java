package com.territorial.auction.domain.auction.entity;

import java.time.LocalDateTime;

public enum AuctionStatus {
    BIDDING,
    IDLE;

    public static AuctionStatus from(LocalDateTime endAt, LocalDateTime now) {
        return now.isAfter(endAt) ? IDLE : BIDDING;
    }
}
