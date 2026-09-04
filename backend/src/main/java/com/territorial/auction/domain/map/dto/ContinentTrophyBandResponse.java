package com.territorial.auction.domain.map.dto;

// ranking-service 대륙 랭킹용 트로피 밴드. upper는 다음 등급 대륙 minTrophyRequired(없으면 Integer.MAX_VALUE).
public record ContinentTrophyBandResponse(int lower, int upper) {}
