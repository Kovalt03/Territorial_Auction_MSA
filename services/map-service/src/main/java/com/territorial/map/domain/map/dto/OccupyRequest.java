package com.territorial.map.domain.map.dto;

import java.time.LocalDateTime;

public record OccupyRequest(
        Long winnerId, LocalDateTime occupiedUntil, LocalDateTime protectedUntil) {}
