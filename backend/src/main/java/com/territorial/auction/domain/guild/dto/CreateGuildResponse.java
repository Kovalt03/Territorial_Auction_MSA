package com.territorial.auction.domain.guild.dto;

import java.time.LocalDateTime;

public record CreateGuildResponse(
        Long guildId,
        String name,
        Long masterId,
        String masterNickname,
        int memberCount,
        LocalDateTime createdAt) {}
