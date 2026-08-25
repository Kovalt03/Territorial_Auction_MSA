package com.territorial.auction.domain.military.dto;

import java.util.List;

/**
 * 공성 대상 정찰(intel) 응답 — 공격자에게 대상 영토의 존별 실제 HP와 정밀 공격 대상 건물 목록을 제공한다. 방어 유닛 구성은 정보 비대칭(공성전 설계 §7-①)상
 * 노출하지 않는다.
 */
public record SiegeTargetResponse(
        Long territoryId,
        int coordX,
        int coordY,
        List<ZoneHp> zones,
        List<TargetBuilding> buildings) {

    /** 존별 방어 건물 HP 합계 — Zone 클리어 진행도 표시용. */
    public record ZoneHp(int zone, int currentHp, int maxHp, int buildingCount) {}

    /** 정밀 공격 대상이 될 수 있는 건물. 주둔 병력은 포함하지 않는다. posX/posY/width/height는 영토 내부 그리드 표시용. */
    public record TargetBuilding(
            Long buildingId,
            String name,
            String displayName,
            int zone,
            int currentHp,
            int maxHp,
            int posX,
            int posY,
            int width,
            int height,
            boolean isUnderConstruction) {}
}
