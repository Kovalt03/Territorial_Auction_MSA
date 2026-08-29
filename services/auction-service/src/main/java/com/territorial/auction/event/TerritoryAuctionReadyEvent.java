package com.territorial.auction.event;

/** map이 발행: 경매 순환에 편입된 영토. auction이 이 스냅샷으로 경매를 생성한다. */
public record TerritoryAuctionReadyEvent(
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        Long continentId,
        String grade) {}
