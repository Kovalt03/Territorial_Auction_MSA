package com.territorial.auction.dto;

public record AuctionResultAlert(
        Long auctionId,
        Long territoryId,
        int coordX,
        int coordY,
        int finalPrice,
        String result // "WIN" or "LOSE"
        ) {}
