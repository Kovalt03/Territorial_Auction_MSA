package com.territorial.auction.domain.military.dto;

import java.time.LocalDateTime;

public record DeclareSiegeResponse(
        Long siegeId, LocalDateTime resolveAt, Integer attackTokenRemaining) {}
