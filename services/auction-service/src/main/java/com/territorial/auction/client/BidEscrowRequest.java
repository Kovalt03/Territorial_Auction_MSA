package com.territorial.auction.client;

public record BidEscrowRequest(
        Long auctionId,
        Long bidderId,
        int bidAmount,
        Long previousBidderId,
        Integer previousAmount) {}
