package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.auction.entity.Auction;
import com.territorial.auction.domain.auction.entity.AuctionBid;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import java.time.LocalDateTime;

public record AdminUserBidResponse(
        Long auctionId,
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        String grade,
        int myBidPrice,
        int currentPrice,
        LocalDateTime bidAt,
        boolean ongoing) {

    public static AdminUserBidResponse from(AuctionBid bid, LocalDateTime now) {
        Auction auction = bid.getAuction();
        Territory t = auction.getTerritory();
        boolean ongoing = !auction.isSettled() && auction.getEndAt().isAfter(now);
        return new AdminUserBidResponse(
                auction.getId(),
                t.getId(),
                t.getCoordX(),
                t.getCoordY(),
                continentName(t.getContinent()),
                t.getGrade().getGrade(),
                bid.getPrice(),
                auction.getCurrentPrice(),
                bid.getBidAt(),
                ongoing);
    }

    private static String continentName(Continent c) {
        return c.getDisplayName() != null ? c.getDisplayName() : c.getName();
    }
}
