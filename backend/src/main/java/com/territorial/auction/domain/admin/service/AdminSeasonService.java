package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.client.SeasonAdminClient;
import com.territorial.auction.domain.admin.client.SeasonAdminClient.SeasonView;
import com.territorial.auction.domain.admin.dto.AdminCreateSeasonRequest;
import com.territorial.auction.domain.admin.dto.AdminSeasonListResponse;
import com.territorial.auction.domain.admin.dto.AdminSeasonResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSeasonService {

    private final SeasonAdminClient seasonAdminClient;
    private final AdminAuditLogger adminAuditLogger;

    public AdminSeasonListResponse getSeasons() {
        LocalDateTime now = LocalDateTime.now();
        List<AdminSeasonResponse> seasons =
                seasonAdminClient.getSeasons().stream()
                        .map(s -> AdminSeasonResponse.from(s, now))
                        .toList();
        return new AdminSeasonListResponse(seasons);
    }

    // 새 시즌 생성/시작. 진행 중 시즌이 있으면 season-service가 거부(SEASON_ALREADY_ACTIVE). 번호는 자동 증가.
    @Transactional
    public AdminSeasonResponse createSeason(Long adminUserId, AdminCreateSeasonRequest request) {
        LocalDateTime now = LocalDateTime.now();
        SeasonView season = seasonAdminClient.createSeason(request.startedAt(), request.endedAt());

        adminAuditLogger.record(
                adminUserId,
                "SEASON_CREATE",
                "SEASON",
                season.seasonId(),
                Map.of(
                        "seasonNumber",
                        season.seasonNumber(),
                        "startedAt",
                        season.startedAt().toString()));
        return AdminSeasonResponse.from(season, now);
    }

    // 시즌 즉시 종료. endedAt을 현재로 설정 → season-service 스케줄러가 정산.
    @Transactional
    public AdminSeasonResponse endSeason(Long adminUserId, Long seasonId) {
        LocalDateTime now = LocalDateTime.now();
        SeasonView season = seasonAdminClient.endSeason(seasonId);

        adminAuditLogger.record(
                adminUserId,
                "SEASON_END",
                "SEASON",
                seasonId,
                Map.of(
                        "seasonNumber",
                        season.seasonNumber(),
                        "endedAt",
                        season.endedAt() != null ? season.endedAt().toString() : now.toString()));
        return AdminSeasonResponse.from(season, now);
    }
}
