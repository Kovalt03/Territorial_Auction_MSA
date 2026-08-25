package com.territorial.auction.domain.building.dto;

import java.time.LocalDateTime;

public record GlobalVaultResponse(
        long storedGP,
        long capacity,
        LocalDateTime lastTransferAt,
        LocalDateTime nextTransferAvailableAt,
        boolean isTransferAvailable) {}
