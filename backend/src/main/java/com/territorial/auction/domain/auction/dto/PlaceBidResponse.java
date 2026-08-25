package com.territorial.auction.domain.auction.dto;

import java.time.LocalDateTime;

public record PlaceBidResponse(Long auctionId, Integer newPrice, LocalDateTime endAt) {}
