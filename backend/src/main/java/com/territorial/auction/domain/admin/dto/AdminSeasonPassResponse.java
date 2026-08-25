package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.season.entity.SeasonPass;

public record AdminSeasonPassResponse(
        Long seasonPassId,
        String name,
        int costAp,
        int durationDays,
        int islandBonusPct,
        int extraBuilders,
        int taxExemptBonus,
        int buildTimeReductionPct) {

    public static AdminSeasonPassResponse from(SeasonPass pass) {
        return new AdminSeasonPassResponse(
                pass.getId(),
                pass.getName(),
                pass.getCostAp(),
                pass.getDurationDays(),
                pass.getIslandBonusPct(),
                pass.getExtraBuilders(),
                pass.getTaxExemptBonus(),
                pass.getBuildTimeReductionPct());
    }
}
