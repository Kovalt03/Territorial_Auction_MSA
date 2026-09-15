package com.territorial.season.domain.season.service;

import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.UserTrophy;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.SeasonRewardRepository;
import com.territorial.season.domain.season.repository.UserTrophyRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 시즌 종료 배치 오케스트레이터(비트랜잭션). 각 유저 보상은 {@link SeasonRewardIssueService}가 독립 트랜잭션으로 처리하므로, 한 유저의 실패가 다른
 * 유저를 롤백시키지 않고 거대 트랜잭션으로 커넥션을 장기 점유하지도 않는다. 전원 지급이 확인되면 시즌을 종료 처리한다(부분 실패 시 다음 주기에 완료 유저는 건너뛰고 전진
 * 복구).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonEndBatchService {

    private final SeasonRepository seasonRepository;
    private final UserTrophyRepository userTrophyRepository;
    private final SeasonRewardRepository seasonRewardRepository;
    private final SeasonRewardIssueService rewardIssueService;

    public void runIfSeasonEnded() {
        seasonRepository
                .findFirstUnprocessedEndedSeason(LocalDateTime.now())
                .ifPresent(this::processSeasonEnd);
    }

    private void processSeasonEnd(Season season) {
        Long seasonId = season.getId();
        List<UserTrophy> trophies = userTrophyRepository.findAllBySeasonId(seasonId);
        log.info("시즌 종료 배치 시작. seasonId={}, 대상 유저 수={}", seasonId, trophies.size());

        for (UserTrophy trophy : trophies) {
            try {
                rewardIssueService.issueUserReward(
                        seasonId, trophy.getUserId(), trophy.getLeague());
            } catch (Exception e) {
                log.error("시즌 보상 지급 실패 seasonId={} userId={}", seasonId, trophy.getUserId(), e);
            }
        }

        long rewarded = seasonRewardRepository.countBySeasonId(seasonId);
        if (rewarded >= trophies.size()) {
            rewardIssueService.finalizeSeason(seasonId);
            log.info("시즌 종료 배치 완료. seasonId={}, 지급 유저={}", seasonId, rewarded);
        } else {
            log.warn(
                    "시즌 종료 배치 부분 완료 — 다음 주기 재시도. seasonId={}, 지급 {}/{}",
                    seasonId,
                    rewarded,
                    trophies.size());
        }
    }
}
