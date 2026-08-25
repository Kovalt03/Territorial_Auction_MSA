package com.territorial.auction.domain.auction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TerritoryAuctionHistoryResponse(Long territoryId, List<HistoryDto> histories) {

    public record HistoryDto(
            Long auctionId, String winnerNickname, Integer finalPrice, LocalDateTime wonAt) {}
}
