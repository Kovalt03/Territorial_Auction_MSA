package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.auction.entity.Auction;
import com.territorial.auction.domain.auction.entity.AuctionBid;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import java.time.LocalDateTime;

public record AdminUserActiveBidResponse(
        Long auctionId,
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        String grade,
        int myBidPrice,
        int currentPrice,
        boolean topBidder,
        LocalDateTime endAt) {

    public static AdminUserActiveBidResponse from(AuctionBid bid) {
        Auction auction = bid.getAuction();
        Territory t = auction.getTerritory();
        return new AdminUserActiveBidResponse(
                auction.getId(),
                t.getId(),
                t.getCoordX(),
                t.getCoordY(),
                continentName(t.getContinent()),
                t.getGrade().getGrade(),
                bid.getPrice(),
                auction.getCurrentPrice(),
                bid.getPrice().intValue() == auction.getCurrentPrice().intValue(),
                auction.getEndAt());
    }

    private static String continentName(Continent c) {
        return c.getDisplayName() != null ? c.getDisplayName() : c.getName();
    }
}
