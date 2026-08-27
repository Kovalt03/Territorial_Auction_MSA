package com.territorial.auction.domain.map.dto;

import java.time.LocalDateTime;

public record ReleaseRequest(LocalDateTime nextAuctionAt) {}
