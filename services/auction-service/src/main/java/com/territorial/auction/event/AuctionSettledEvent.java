package com.territorial.auction.event;

import java.util.List;

public record AuctionSettledEvent(
        Long auctionId,
        Long territoryId,
        int coordX,
        int coordY,
        Long winnerId,
        String winnerNickname,
        int finalPrice,
        String grade,
        List<Long> runnerUpIds) {}
