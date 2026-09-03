package com.territorial.season.internal;

import com.territorial.season.domain.season.SeasonPassPolicy;
import com.territorial.season.domain.season.entity.SeasonMission.MissionTrigger;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.UserSeasonPassRepository;
import com.territorial.season.domain.season.repository.UserTrophyRepository;
import com.territorial.season.domain.season.service.MissionService;
import com.territorial.season.domain.season.service.SeasonXpService;
import com.territorial.season.internal.dto.SeasonInternalDtos.ActiveSeasonView;
import com.territorial.season.internal.dto.SeasonInternalDtos.SeasonPassBenefitView;
import com.territorial.season.internal.dto.SeasonInternalDtos.TrophyView;
import com.territorial.season.internal.dto.SeasonInternalDtos.UserPassSummaryView;
import com.territorial.season.internal.dto.SeasonInternalDtos.UserScoreView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서비스 간 내부 계약 처리. 트로피·활성시즌·게임이벤트·시즌패스혜택을 위임 소비자에게 제공한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonInternalService {

    private final SeasonRepository seasonRepository;
    private final UserTrophyRepository userTrophyRepository;
    private final UserSeasonPassRepository userSeasonPassRepository;
    private final SeasonXpService seasonXpService;
    private final MissionService missionService;

    public Optional<ActiveSeasonView> getActiveSeason() {
        return seasonRepository
                .findActiveSeason(LocalDateTime.now())
                .map(
                        s ->
                                new ActiveSeasonView(
                                        s.getId(),
                                        s.getSeasonNumber(),
                                        s.getStartedAt(),
                                        s.getEndedAt()));
    }

    public Optional<TrophyView> getTrophy(Long userId) {
        return userTrophyRepository.findById(userId).map(TrophyView::from);
    }

    public List<TrophyView> getTrophyRanking(int page, int size) {
        return userTrophyRepository.findAllByOrderByScoreDesc(PageRequest.of(page, size)).stream()
                .map(TrophyView::from)
                .toList();
    }

    public long countTrophyAbove(int score) {
        return userTrophyRepository.countByScoreGreaterThan(score);
    }

    public List<TrophyView> getTrophyBand(int lower, int upper, int page, int size) {
        return userTrophyRepository
                .findInScoreBandOrderByScoreDesc(lower, upper, PageRequest.of(page, size))
                .stream()
                .map(TrophyView::from)
                .toList();
    }

    public long countTrophyBand(int score, int upper) {
        return userTrophyRepository.countByScoreGreaterThanAndScoreLessThan(score, upper);
    }

    public List<UserScoreView> sumScores(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userTrophyRepository.sumScoreGroupByUserIds(userIds).stream()
                .map(row -> new UserScoreView((Long) row[0], (long) row[1]))
                .toList();
    }

    /** user 프로필용 활성 시즌패스 요약 — 만료 필터 없이 최신 활성 패스를 그대로 노출(모놀 기존 동작 보존). */
    public Optional<UserPassSummaryView> getUserPassSummary(Long userId) {
        return userSeasonPassRepository
                .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId)
                .map(
                        pass ->
                                new UserPassSummaryView(
                                        pass.getExpiresAt(),
                                        pass.getSeasonPass().getExtraBuilders()));
    }

    public SeasonPassBenefitView getSeasonPassBenefit(Long userId) {
        int taxExemptBonus =
                userSeasonPassRepository
                        .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId)
                        .filter(pass -> pass.getExpiresAt().isAfter(LocalDateTime.now()))
                        .map(pass -> pass.getSeasonPass().getTaxExemptBonus())
                        .orElse(0);
        return new SeasonPassBenefitView(taxExemptBonus);
    }

    /** 게임 이벤트 위임 처리 — XP 적립 + 미션 진행. 활성 시즌은 season-service가 자체 조회. */
    @Transactional
    public void handleGameEvent(Long userId, String eventType) {
        switch (eventType) {
            case "AUCTION_WIN" -> {
                seasonXpService.grantXpForEvent(userId, SeasonPassPolicy.XP_AUCTION_WIN);
                missionService.recordProgress(userId, MissionTrigger.AUCTION_WIN);
            }
            case "SIEGE_WIN" -> {
                seasonXpService.grantXpForEvent(userId, SeasonPassPolicy.XP_SIEGE_VICTORY);
                missionService.recordProgress(userId, MissionTrigger.SIEGE_WIN);
            }
            default -> {
                /* 알 수 없는 이벤트 타입 무시 */
            }
        }
    }
}
