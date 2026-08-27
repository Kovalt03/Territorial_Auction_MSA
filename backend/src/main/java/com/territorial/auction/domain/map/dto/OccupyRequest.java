package com.territorial.auction.domain.map.dto;

import java.time.LocalDateTime;

public record OccupyRequest(
        Long winnerId, LocalDateTime occupiedUntil, LocalDateTime protectedUntil) {}
