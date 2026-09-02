package com.territorial.combat.domain.building.dto;

import java.time.LocalDateTime;

/** 수리는 즉시 완료가 아니라 시간이 걸린다. buildCompleteAt 까지 비활성(생산·방어 정지), 완료 시 HP 풀피. */
public record RepairBuildingResponse(
        Long buildingId, int hp, LocalDateTime buildCompleteAt, int gpRemaining) {}
