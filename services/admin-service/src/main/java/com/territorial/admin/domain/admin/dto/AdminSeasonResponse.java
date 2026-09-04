package com.territorial.admin.domain.admin.dto;

import com.territorial.admin.client.SeasonAdminClient.SeasonView;
import java.time.LocalDateTime;

public record AdminSeasonResponse(
        Long seasonId,
        Integer seasonNumber,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime processedAt,
        String status) {

    public static AdminSeasonResponse from(SeasonView s, LocalDateTime now) {
        return new AdminSeasonResponse(
                s.seasonId(),
                s.seasonNumber(),
                s.startedAt(),
                s.endedAt(),
                s.processedAt(),
                status(s, now));
    }

    private static String status(SeasonView s, LocalDateTime now) {
        if (s.startedAt().isAfter(now)) return "SCHEDULED";
        if (s.endedAt() == null || s.endedAt().isAfter(now)) return "ACTIVE";
        return s.processedAt() != null ? "PROCESSED" : "ENDED";
    }
}
