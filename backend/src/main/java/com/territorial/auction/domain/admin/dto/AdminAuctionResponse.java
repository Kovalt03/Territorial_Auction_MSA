package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.auction.entity.Auction;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import java.time.LocalDateTime;

public record AdminAuctionResponse(
        Long auctionId,
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        String grade,
        int currentPrice,
        Long currentBidderId,
        String currentBidderNickname,
        LocalDateTime endAt) {

    public static AdminAuctionResponse from(Auction a) {
        Territory t = a.getTerritory();
        var bidder = a.getCurrentBidder();
        return new AdminAuctionResponse(
                a.getId(),
                t.getId(),
                t.getCoordX(),
                t.getCoordY(),
                continentName(t.getContinent()),
                t.getGrade().getGrade(),
                a.getCurrentPrice(),
                bidder != null ? bidder.getId() : null,
                bidder != null ? bidder.getNickname() : null,
                a.getEndAt());
    }

    private static String continentName(Continent c) {
        return c.getDisplayName() != null ? c.getDisplayName() : c.getName();
    }
}
