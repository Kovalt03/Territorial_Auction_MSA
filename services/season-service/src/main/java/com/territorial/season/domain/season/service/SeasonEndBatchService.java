package com.territorial.season.domain.season.service;

import com.territorial.season.client.CombatResourceClient;
import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.SeasonReward;
import com.territorial.season.domain.season.entity.UserTrophy;
import com.territorial.season.domain.season.entity.UserTrophy.League;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.SeasonRewardRepository;
import com.territorial.season.domain.season.repository.UserSeasonPassRepository;
import com.territorial.season.domain.season.repository.UserTrophyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonEndBatchService {

    private record RewardSpec(int gp, int normalToken, int precisionToken, String title) {}

    private static final Map<League, RewardSpec> REWARD_TABLE =
            Map.of(
                    League.BRONZE, new RewardSpec(100, 0, 0, null),
                    League.SILVER, new RewardSpec(300, 1, 0, null),
                    League.GOLD, new RewardSpec(600, 1, 1, null),
                    League.DIAMOND, new RewardSpec(1200, 2, 2, null),
                    League.CHAMPION, new RewardSpec(3000, 3, 3, "CHAMPION"));

    private final SeasonRepository seasonRepository;
    private final UserTrophyRepository userTrophyRepository;
    private final SeasonRewardRepository seasonRewardRepository;
    private final CombatResourceClient combatResourceClient;
    private final UserSeasonPassRepository userSeasonPassRepository;

    @Transactional
    public void runIfSeasonEnded() {
        seasonRepository
                .findFirstUnprocessedEndedSeason(LocalDateTime.now())
                .ifPresent(this::processSeasonEnd);
    }

    private void processSeasonEnd(Season season) {
        log.info("시즌 종료 배치 시작. seasonId={}", season.getId());
        List<UserTrophy> trophies = userTrophyRepository.findAllBySeasonId(season.getId());
        issueRewards(season, trophies);
        resetTrophies(season, trophies);
        int deactivated = userSeasonPassRepository.deactivateAllActive();
        season.markProcessed();
        log.info(
                "시즌 종료 배치 완료. seasonId={}, 대상 유저 수={}, 패스 일괄 종료={}",
                season.getId(),
                trophies.size(),
                deactivated);
    }

    private void issueRewards(Season season, List<UserTrophy> trophies) {
        for (UserTrophy trophy : trophies) {
            if (seasonRewardRepository.existsBySeasonIdAndUserId(
                    season.getId(), trophy.getUserId())) {
                continue;
            }
            RewardSpec spec = REWARD_TABLE.get(trophy.getLeague());
            saveRewardRecord(trophy, season, spec);
            creditVault(trophy.getUserId(), season.getId(), spec);
            creditAttackTokens(trophy.getUserId(), season.getId(), spec);
        }
    }

    private void saveRewardRecord(UserTrophy trophy, Season season, RewardSpec spec) {
        seasonRewardRepository.save(
                SeasonReward.builder()
                        .userId(trophy.getUserId())
                        .season(season)
                        .league(trophy.getLeague().name())
                        .gpReward(spec.gp())
                        .attackTokenNormal(spec.normalToken())
                        .attackTokenPrecision(spec.precisionToken())
                        .titleReward(spec.title())
                        .build());
    }

    // 시즌 보상 GP는 위치가 없으므로 금고로 적립한다. 금고가 없으면 만든다.
    private void creditVault(Long userId, Long seasonId, RewardSpec spec) {
        if (spec.gp() <= 0) return;
        combatResourceClient.creditGp(
                userId, spec.gp(), "SEASON_END:" + seasonId + ":" + userId + ":GP");
    }

    private void creditAttackTokens(Long userId, Long seasonId, RewardSpec spec) {
        if (spec.normalToken() == 0 && spec.precisionToken() == 0) return;
        combatResourceClient.creditAttackTokens(
                userId,
                spec.normalToken(),
                spec.precisionToken(),
                "SEASON_END:" + seasonId + ":" + userId + ":ATTACK_TOKEN");
    }

    private void resetTrophies(Season season, List<UserTrophy> trophies) {
        for (UserTrophy trophy : trophies) {
            trophy.applySeasonReset(season.getId());
        }
    }
}
