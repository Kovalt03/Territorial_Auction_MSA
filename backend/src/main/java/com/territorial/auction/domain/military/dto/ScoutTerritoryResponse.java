package com.territorial.auction.domain.military.dto;

/** 정찰 결과. 방어 유닛의 <b>총 병력 수만</b> 공개하고 종류·Zone 분포·건물 배치는 노출하지 않는다(정보 비대칭). */
public record ScoutTerritoryResponse(Long territoryId, int defenderTotalUnits) {}
