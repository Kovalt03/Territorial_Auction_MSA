package com.territorial.social.domain.guild.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GuildDetailResponse(
        Long guildId,
        String name,
        String description,
        String emblem,
        MasterInfo master,
        long memberCount,
        long totalTerritoryCount,
        List<MemberInfo> members,
        LocalDateTime createdAt) {
    public record MasterInfo(Long userId, String nickname) {}

    public record MemberInfo(
            Long userId,
            String nickname,
            String role,
            long territoryCount,
            LocalDateTime joinedAt) {}
}
