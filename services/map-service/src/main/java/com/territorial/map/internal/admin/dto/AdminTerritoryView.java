package com.territorial.map.internal.admin.dto;

import com.territorial.map.domain.map.entity.Territory;

/** 관리자 영토 목록/변경 응답. ownerNickname은 user-service NicknameClient로 별도 주입한다. */
public record AdminTerritoryView(
        Long territoryId,
        int coordX,
        int coordY,
        String grade,
        String status,
        String ownerNickname,
        boolean auctionEnabled) {

    public static AdminTerritoryView from(Territory t, String ownerNickname) {
        return new AdminTerritoryView(
                t.getId(),
                t.getCoordX(),
                t.getCoordY(),
                t.getGrade().getGrade(),
                t.getStatus().name(),
                ownerNickname,
                t.getAuctionEnabled());
    }
}
