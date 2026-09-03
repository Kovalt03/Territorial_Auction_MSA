package com.territorial.season.domain.season.service;

import com.territorial.season.domain.season.dto.ClaimMissionResponse;
import com.territorial.season.domain.season.dto.MissionListResponse;
import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.SeasonMission;
import com.territorial.season.domain.season.entity.SeasonMission.MissionPeriod;
import com.territorial.season.domain.season.entity.SeasonMission.MissionTrigger;
import com.territorial.season.domain.season.entity.SeasonPassProgress;
import com.territorial.season.domain.season.entity.UserMissionProgress;
import com.territorial.season.domain.season.repository.SeasonMissionRepository;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.UserMissionProgressRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.season.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

    private static final String CACHE_PROGRESS = "season_pass:progress:";

    private final SeasonMissionRepository seasonMissionRepository;
    private final UserMissionProgressRepository userMissionProgressRepository;
    private final SeasonRepository seasonRepository;    private final SeasonXpService seasonXpService;
    private final RedisTemplate<String, Object> redisTemplate;

    public MissionListResponse getMissions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Season season = findActiveSeason(now);

        List<SeasonMission> missions =
                seasonMissionRepository.findBySeason_IdOrderBySortOrderAsc(season.getId());
        Map<Long, UserMissionProgress> progressMap =
                userMissionProgressRepository
                        .findByUserIdAndMission_Season_Id(userId, season.getId())
                        .stream()
                        .collect(
                                Collectors.toMap(p -> p.getMission().getId(), Function.identity()));

        List<MissionListResponse.MissionItem> items =
                missions.stream().map(m -> toItem(m, progressMap.get(m.getId()), now)).toList();
        return new MissionListResponse(items);
    }

    @Transactional
    public ClaimMissionResponse claimMission(Long userId, Long missionId) {
        LocalDateTime now = LocalDateTime.now();
        SeasonMission mission =
                seasonMissionRepository
                        .findById(missionId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_MISSION_NOT_FOUND));

        UserMissionProgress progress = findOrCreateProgress(userId, mission, now);
        if (isPeriodExpired(mission.getPeriod(), progress.getPeriodStartedAt(), now)) {
            progress.reset(now);
        }
        if (progress.isClaimed()) {
            throw new CustomException(ErrorCode.MISSION_ALREADY_CLAIMED);
        }

        if (mission.isSelfClaim()) {
            progress.completeAndClaim(mission.getGoalCount());
        } else {
            if (progress.getCompletedCount() < mission.getGoalCount()) {
                throw new CustomException(ErrorCode.MISSION_NOT_COMPLETED);
            }
            progress.markClaimed();
        }

        SeasonPassProgress sp =
                seasonXpService.grantXpDirectly(
                        userId, mission.getSeason().getId(), mission.getXpReward());
        invalidateProgressCache(userId);

        int newLevel = sp != null ? sp.getLevel() : 1;
        int newXp = sp != null ? sp.getXp() : 0;
        log.info("미션 수령. userId={}, missionId={}, xp={}", userId, missionId, mission.getXpReward());
        return new ClaimMissionResponse(missionId, mission.getXpReward(), newLevel, newXp);
    }

    /** 게임 이벤트(낙찰/공성 승리)로 미션 진행도 증가. 이벤트 리스너에서 호출. */
    @Transactional
    public void recordProgress(Long userId, MissionTrigger trigger) {
        LocalDateTime now = LocalDateTime.now();
        Season season = seasonRepository.findActiveSeason(now).orElse(null);
        if (season == null) return;

        List<SeasonMission> missions =
                seasonMissionRepository.findBySeason_IdAndTriggerType(season.getId(), trigger);
        for (SeasonMission mission : missions) {
            UserMissionProgress progress = findOrCreateProgress(userId, mission, now);
            if (isPeriodExpired(mission.getPeriod(), progress.getPeriodStartedAt(), now)) {
                progress.reset(now);
            }
            progress.increment(1);
        }
    }

    private MissionListResponse.MissionItem toItem(
            SeasonMission m, UserMissionProgress progress, LocalDateTime now) {
        boolean expired =
                progress != null
                        && isPeriodExpired(m.getPeriod(), progress.getPeriodStartedAt(), now);
        int completed = (progress == null || expired) ? 0 : progress.getCompletedCount();
        boolean claimed = progress != null && !expired && progress.isClaimed();
        boolean canClaim = !claimed && (m.isSelfClaim() || completed >= m.getGoalCount());
        return new MissionListResponse.MissionItem(
                m.getId(),
                m.getCode(),
                m.getTitle(),
                m.getDescription(),
                m.getPeriod().name(),
                m.getGoalCount(),
                completed,
                m.getXpReward(),
                claimed,
                canClaim);
    }

    private UserMissionProgress findOrCreateProgress(
            Long userId, SeasonMission mission, LocalDateTime now) {
        return userMissionProgressRepository
                .findByUserIdAndMission_Id(userId, mission.getId())
                .orElseGet(
                        () -> {
                            return userMissionProgressRepository.save(
                                    UserMissionProgress.builder()
                                            .userId(userId)
                                            .mission(mission)
                                            .periodStartedAt(now)
                                            .build());
                        });
    }

    private boolean isPeriodExpired(
            MissionPeriod period, LocalDateTime startedAt, LocalDateTime now) {
        return switch (period) {
            case DAILY -> startedAt.toLocalDate().isBefore(now.toLocalDate());
            case WEEKLY ->
                    startedAt.isBefore(now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay());
            case SEASON -> false;
        };
    }

    private Season findActiveSeason(LocalDateTime now) {
        return seasonRepository
                .findActiveSeason(now)
                .orElseThrow(() -> new CustomException(ErrorCode.SEASON_NOT_FOUND));
    }

    private void invalidateProgressCache(Long userId) {
        try {
            redisTemplate.delete(CACHE_PROGRESS + userId);
        } catch (Exception e) {
            log.warn("미션 수령 후 진행도 캐시 무효화 실패. userId={}", userId);
        }
    }
}
