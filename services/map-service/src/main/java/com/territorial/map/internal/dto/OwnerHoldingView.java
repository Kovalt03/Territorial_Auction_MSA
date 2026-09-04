package com.territorial.map.internal.dto;

import com.territorial.map.domain.map.TerritoryPolicy;
import com.territorial.map.domain.map.entity.Continent;
import com.territorial.map.domain.map.entity.Territory;
import java.time.LocalDateTime;

/** 유저 보유 영토 표시용(내 영토 화면). occupiedAt은 점유 시작 시각(점유 만료 - 점유 기간)으로 map이 파생 제공한다. */
public record OwnerHoldingView(
        Long territoryId,
        String grade,
        int coordX,
        int coordY,
        String continentName,
        LocalDateTime occupiedAt,
        LocalDateTime occupiedUntil) {

    public static OwnerHoldingView from(Territory t) {
        return new OwnerHoldingView(
                t.getId(),
                t.getGrade().getGrade(),
                t.getCoordX(),
                t.getCoordY(),
                continentName(t.getContinent()),
                deriveOccupiedAt(t),
                t.getOccupiedUntil());
    }

    private static String continentName(Continent c) {
        return c.getDisplayName() != null ? c.getDisplayName() : c.getName();
    }

    private static LocalDateTime deriveOccupiedAt(Territory t) {
        if (t.getOccupiedUntil() == null) return null;
        return t.getOccupiedUntil().minusDays(TerritoryPolicy.OCCUPATION_DURATION_DAYS);
    }
}
