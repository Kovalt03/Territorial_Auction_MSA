package com.territorial.user.domain.user.dto;

import java.time.LocalDateTime;

public record MyProfileResponse(
        Long userId,
        String nickname,
        WalletInfo wallet,
        IslandInfo island,
        SeasonPassInfo seasonPass,
        int territoryCount) {

    public record WalletInfo(int availableGP, int availableAP, int lockedAP) {}

    public record IslandInfo(Long islandId, int level, int productionRate, int builderCount) {}

    public record SeasonPassInfo(boolean isActive, LocalDateTime expiresAt) {}
}
