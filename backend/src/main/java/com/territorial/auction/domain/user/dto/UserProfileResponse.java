package com.territorial.auction.domain.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        int level,
        int trophyPoints,
        int territoryCount,
        String guildName,
        LocalDateTime joinedAt) {}
