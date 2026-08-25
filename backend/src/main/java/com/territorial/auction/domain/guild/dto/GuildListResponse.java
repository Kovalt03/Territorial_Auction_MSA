package com.territorial.auction.domain.guild.dto;

import java.util.List;

public record GuildListResponse(long totalCount, int page, int size, List<GuildSummary> guilds) {

    public record GuildSummary(
            Long guildId,
            String guildName,
            String masterNickname,
            long memberCount,
            int maxMembers,
            long totalTrophyPoints,
            long totalTerritories,
            String recruitingStatus) {}
}
