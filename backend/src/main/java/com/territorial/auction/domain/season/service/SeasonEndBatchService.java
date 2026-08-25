package com.territorial.auction.domain.season.service;

import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.military.entity.AttackToken;
import com.territorial.auction.domain.military.repository.AttackTokenRepository;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.entity.SeasonReward;
import com.territorial.auction.domain.season.entity.UserTrophy;
import com.territorial.auction.domain.season.entity.UserTrophy.League;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.season.repository.SeasonRewardRepository;
import com.territorial.auction.domain.season.repository.UserSeasonPassRepository;
import com.territorial.auction.domain.season.repository.UserTrophyRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
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
    private final GlobalVaultRepository globalVaultRepository;
    private final UserRepository userRepository;
    private final AttackTokenRepository attackTokenRepository;
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
            creditVault(trophy.getUserId(), spec);
            creditAttackTokens(trophy.getUserId(), spec);
        }
    }

    private void saveRewardRecord(UserTrophy trophy, Season season, RewardSpec spec) {
        seasonRewardRepository.save(
                SeasonReward.builder()
                        .user(trophy.getUser())
                        .season(season)
                        .league(trophy.getLeague().name())
                        .gpReward(spec.gp())
                        .attackTokenNormal(spec.normalToken())
                        .attackTokenPrecision(spec.precisionToken())
                        .titleReward(spec.title())
                        .build());
    }

    // 시즌 보상 GP는 위치가 없으므로 금고로 적립한다. 금고가 없으면 만든다.
    private void creditVault(Long userId, RewardSpec spec) {
        if (spec.gp() <= 0) return;
        globalVaultRepository
                .findByIdWithLock(userId)
                .orElseGet(
                        () ->
                                globalVaultRepository.save(
                                        GlobalVault.builder()
                                                .user(userRepository.getReferenceById(userId))
                                                .build()))
                .receiveGp(spec.gp());
    }

    private void creditAttackTokens(Long userId, RewardSpec spec) {
        if (spec.normalToken() == 0 && spec.precisionToken() == 0) return;
        AttackToken token =
                attackTokenRepository
                        .findByUserIdWithLock(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "AttackToken not found for userId=" + userId));
        token.addNormal(spec.normalToken());
        token.addPrecision(spec.precisionToken());
    }

    private void resetTrophies(Season season, List<UserTrophy> trophies) {
        for (UserTrophy trophy : trophies) {
            trophy.applySeasonReset(season.getId());
        }
    }
}
