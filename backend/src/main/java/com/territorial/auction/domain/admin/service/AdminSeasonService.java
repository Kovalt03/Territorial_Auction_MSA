package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminCreateSeasonRequest;
import com.territorial.auction.domain.admin.dto.AdminSeasonListResponse;
import com.territorial.auction.domain.admin.dto.AdminSeasonResponse;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
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

    private final SeasonRepository seasonRepository;
    private final AdminAuditLogger adminAuditLogger;

    public AdminSeasonListResponse getSeasons() {
        LocalDateTime now = LocalDateTime.now();
        List<AdminSeasonResponse> seasons =
                seasonRepository.findAllByOrderBySeasonNumberDesc().stream()
                        .map(s -> AdminSeasonResponse.from(s, now))
                        .toList();
        return new AdminSeasonListResponse(seasons);
    }

    // 새 시즌 생성/시작. 진행 중 시즌이 있으면 거부(동시 활성 시즌 방지). 번호는 자동 증가.
    @Transactional
    public AdminSeasonResponse createSeason(Long adminUserId, AdminCreateSeasonRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (seasonRepository.findActiveSeason(now).isPresent()) {
            throw new CustomException(ErrorCode.SEASON_ALREADY_ACTIVE);
        }
        LocalDateTime startedAt = request.startedAt() != null ? request.startedAt() : now;
        int seasonNumber = seasonRepository.findMaxSeasonNumber() + 1;
        Season season =
                seasonRepository.save(
                        Season.builder()
                                .seasonNumber(seasonNumber)
                                .startedAt(startedAt)
                                .endedAt(request.endedAt())
                                .build());

        adminAuditLogger.record(
                adminUserId,
                "SEASON_CREATE",
                "SEASON",
                season.getId(),
                Map.of("seasonNumber", seasonNumber, "startedAt", startedAt.toString()));
        return AdminSeasonResponse.from(season, now);
    }

    // 시즌 즉시 종료. endedAt을 현재로 설정 → 스케줄러가 정산.
    @Transactional
    public AdminSeasonResponse endSeason(Long adminUserId, Long seasonId) {
        Season season =
                seasonRepository
                        .findById(seasonId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_BY_ID_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (season.getEndedAt() != null && season.getEndedAt().isBefore(now)) {
            throw new CustomException(ErrorCode.SEASON_ALREADY_ENDED);
        }
        season.endNow(now);

        adminAuditLogger.record(
                adminUserId,
                "SEASON_END",
                "SEASON",
                seasonId,
                Map.of("seasonNumber", season.getSeasonNumber(), "endedAt", now.toString()));
        return AdminSeasonResponse.from(season, now);
    }
}
