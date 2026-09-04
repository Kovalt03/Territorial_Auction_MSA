package com.territorial.map.domain.map.event;

/** 경매 순환에 편입된 영토. auction-service가 구독해 경매를 생성한다. 필드명은 auction 측 이벤트와 일치해야 한다(JSON). */
public record TerritoryAuctionReadyEvent(
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        Long continentId,
        String grade) {}
