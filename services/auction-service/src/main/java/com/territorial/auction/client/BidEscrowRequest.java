package com.territorial.auction.client;

public record BidEscrowRequest(
        Long bidderId, int bidAmount, Long previousBidderId, Integer previousAmount) {}
