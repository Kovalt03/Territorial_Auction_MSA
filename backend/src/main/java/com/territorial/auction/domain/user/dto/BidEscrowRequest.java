package com.territorial.auction.domain.user.dto;

public record BidEscrowRequest(Long bidderId, int bidAmount, Long previousBidderId, Integer previousAmount) {}
