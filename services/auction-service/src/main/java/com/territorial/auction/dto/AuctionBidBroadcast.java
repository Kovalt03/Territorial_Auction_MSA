package com.territorial.auction.dto;

import java.time.LocalDateTime;

public record AuctionBidBroadcast(
        Long auctionId,
        int currentPrice,
        Long bidderId,
        String bidderNickname,
        LocalDateTime bidAt,
        LocalDateTime endAt,
        Long previousBidderId,
        int coordX,
        int coordY) {}
