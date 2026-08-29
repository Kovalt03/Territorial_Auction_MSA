package com.territorial.auction.domain.user.dto;

public record ConsumeLockedRequest(Long winnerId, int finalPrice, Long auctionId) {}
