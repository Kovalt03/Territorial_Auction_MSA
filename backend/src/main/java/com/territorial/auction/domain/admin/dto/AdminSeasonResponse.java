package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.season.entity.Season;
import java.time.LocalDateTime;

public record AdminSeasonResponse(
        Long seasonId,
        Integer seasonNumber,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime processedAt,
        String status) {

    public static AdminSeasonResponse from(Season s, LocalDateTime now) {
        return new AdminSeasonResponse(
                s.getId(),
                s.getSeasonNumber(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getProcessedAt(),
                status(s, now));
    }

    private static String status(Season s, LocalDateTime now) {
        if (s.getStartedAt().isAfter(now)) return "SCHEDULED";
        if (s.getEndedAt() == null || s.getEndedAt().isAfter(now)) return "ACTIVE";
        return s.getProcessedAt() != null ? "PROCESSED" : "ENDED";
    }
}
