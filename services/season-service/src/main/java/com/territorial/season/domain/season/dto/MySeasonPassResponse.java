package com.territorial.season.domain.season.dto;

import com.territorial.season.domain.season.entity.SeasonPass;
import com.territorial.season.domain.season.entity.UserSeasonPass;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record MySeasonPassResponse(Boolean hasSeasonPass, SeasonPassInfo seasonPass) {

    public record SeasonPassInfo(
            Long seasonPassId,
            String name,
            LocalDateTime startedAt,
            LocalDateTime expiresAt,
            long daysRemaining,
            BenefitInfo benefits) {}

    public record BenefitInfo(int islandBonusPct, int extraBuilders, int taxExemptBonus) {}

    public static MySeasonPassResponse from(UserSeasonPass userSeasonPass) {
        if (userSeasonPass == null) {
            return new MySeasonPassResponse(false, null);
        }

        SeasonPass pass = userSeasonPass.getSeasonPass();
        long daysRemaining =
                ChronoUnit.DAYS.between(LocalDateTime.now(), userSeasonPass.getExpiresAt());

        return new MySeasonPassResponse(
                true,
                new SeasonPassInfo(
                        pass.getId(),
                        pass.getName(),
                        userSeasonPass.getStartedAt(),
                        userSeasonPass.getExpiresAt(),
                        daysRemaining,
                        new BenefitInfo(
                                pass.getIslandBonusPct(),
                                pass.getExtraBuilders(),
                                pass.getTaxExemptBonus())));
    }
}
