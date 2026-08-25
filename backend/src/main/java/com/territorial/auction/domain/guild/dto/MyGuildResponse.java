package com.territorial.auction.domain.guild.dto;

import java.time.LocalDateTime;

public record MyGuildResponse(
        Long guildId,
        String guildName,
        String description,
        String masterNickname,
        long memberCount,
        int maxMembers,
        long totalTerritories,
        long totalTrophyPoints,
        String myRole,
        LocalDateTime joinedAt) {}
