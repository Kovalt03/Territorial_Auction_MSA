package com.territorial.auction.domain.map.dto;

public record MapUpdateBroadcast(
        Long territoryId,
        int coordX,
        int coordY,
        Long ownerId, // null if IDLE
        String ownerNickname, // null if IDLE
        String status // "OCCUPIED" or "IDLE"
        ) {}
