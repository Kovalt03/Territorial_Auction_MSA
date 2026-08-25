package com.territorial.auction.domain.building.dto;

/** AP로 건설/업그레이드를 즉시 완료한 결과. */
public record RushConstructionResponse(Long buildingId, int apSpent, int apRemaining) {}
