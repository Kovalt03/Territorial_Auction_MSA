package com.territorial.auction.domain.building.dto;

import java.time.LocalDateTime;

/** AP 생산 부스터 발동 결과 — 종료 시각·배율·소모 AP·잔여 AP. */
public record ProductionBoostResponse(
        LocalDateTime boostUntil, int multiplier, int apSpent, int apRemaining) {}
