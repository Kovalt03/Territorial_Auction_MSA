package com.territorial.admin.domain.admin.dto;

import com.territorial.admin.client.SeasonAdminClient.SeasonPassView;

public record AdminSeasonPassResponse(
        Long seasonPassId,
        String name,
        int costAp,
        int durationDays,
        int islandBonusPct,
        int extraBuilders,
        int taxExemptBonus,
        int buildTimeReductionPct) {

    public static AdminSeasonPassResponse from(SeasonPassView pass) {
        return new AdminSeasonPassResponse(
                pass.seasonPassId(),
                pass.name(),
                pass.costAp(),
                pass.durationDays(),
                pass.islandBonusPct(),
                pass.extraBuilders(),
                pass.taxExemptBonus(),
                pass.buildTimeReductionPct());
    }
}
