package com.territorial.season.domain.season.dto;

import com.territorial.season.domain.season.entity.SeasonPass;
import com.territorial.season.domain.season.entity.UserSeasonPass;
import java.time.LocalDateTime;

public record PurchaseSeasonPassResponse(
        Long seasonPassId,
        String name,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        int costAP,
        int remainingAP,
        BenefitInfo benefits) {
    public static PurchaseSeasonPassResponse of(UserSeasonPass userPass, int remainingAp) {
        SeasonPass pass = userPass.getSeasonPass();

        return new PurchaseSeasonPassResponse(
                pass.getId(),
                pass.getName(),
                userPass.getStartedAt(),
                userPass.getExpiresAt(),
                pass.getCostAp(),
                remainingAp,
                new BenefitInfo(
                        pass.getIslandBonusPct(),
                        pass.getExtraBuilders(),
                        pass.getTaxExemptBonus()));
    }

    public record BenefitInfo(int islandBonusPct, int extraBuilders, int taxExemptBonus) {}
}
