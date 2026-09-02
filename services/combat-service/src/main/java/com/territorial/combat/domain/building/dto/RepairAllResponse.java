package com.territorial.combat.domain.building.dto;

/** 전체 수리 결과 — 수리에 들어간 건물 수·총 비용·위치 저장소 잔여 GP. */
public record RepairAllResponse(int repairedCount, int totalCost, int gpRemaining) {}
