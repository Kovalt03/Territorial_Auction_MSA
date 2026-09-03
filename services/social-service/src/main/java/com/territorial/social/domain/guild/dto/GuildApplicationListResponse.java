package com.territorial.social.domain.guild.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GuildApplicationListResponse(Long guildId, List<ApplicationInfo> applications) {
    public record ApplicationInfo(
            Long applicationId,
            Long userId,
            String nickname,
            int trophyPoints,
            LocalDateTime appliedAt) {}
}
