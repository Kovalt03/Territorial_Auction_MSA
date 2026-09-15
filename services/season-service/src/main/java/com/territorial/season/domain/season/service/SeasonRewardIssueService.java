package com.territorial.season.domain.season.service;

import com.territorial.season.client.CombatResourceClient;
import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.SeasonReward;
import com.territorial.season.domain.season.entity.UserTrophy.League;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.SeasonRewardRepository;
import com.territorial.season.domain.season.repository.UserSeasonPassRepository;
import com.territorial.season.domain.season.repository.UserTrophyRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 종료 보상의 유저 단위 지급/종료 처리. 각 유저를 <b>독립 트랜잭션</b>으로 처리해, 한 유저의 실패가 전체를 롤백시키거나 거대 트랜잭션으로 커넥션을 장기 점유하지
 * 않게 한다(구 processSeasonEnd는 전체 유저 루프를 단일 트랜잭션에서 크로스서비스 HTTP로 돌았다).
 *
 * <p>combat 지급은 멱등(commandKey)이라, 지급을 먼저 하고 로컬 reward 레코드를 마지막에 커밋한다 — 롤백 시 재실행이 안전하고(재지급은 멱등
 * no-op), reward 레코드가 "지급 완료" durable 마커가 되어 재실행이 완료 유저를 건너뛴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonRewardIssueService {

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

    /**
     * 한 유저의 시즌 보상 지급 + 트로피 리셋(유저별 독립 트랜잭션). 이미 지급됐으면 건너뛴다(reward 레코드 = 완료 마커, 리셋도 같은 트랜잭션에서 함께
     * 커밋됐으므로 재수행 불필요). combat 지급을 먼저(멱등) 하고 로컬 커밋을 마지막에 둬 롤백 시 재실행이 안전하다.
     */
    @Transactional
    public void issueUserReward(Long seasonId, Long userId, League league) {
        if (seasonRewardRepository.existsBySeasonIdAndUserId(seasonId, userId)) {
            return;
        }
        RewardSpec spec = REWARD_TABLE.get(league);
        creditVault(userId, seasonId, spec);
        creditAttackTokens(userId, seasonId, spec);

        Season season =
                seasonRepository
                        .findById(seasonId)
                        .orElseThrow(() -> new IllegalStateException("시즌을 찾을 수 없습니다: " + seasonId));
        seasonRewardRepository.save(
                SeasonReward.builder()
                        .userId(userId)
                        .season(season)
                        .league(league.name())
                        .gpReward(spec.gp())
                        .attackTokenNormal(spec.normalToken())
                        .attackTokenPrecision(spec.precisionToken())
                        .titleReward(spec.title())
                        .build());

        userTrophyRepository
                .findById(userId)
                .ifPresent(trophy -> trophy.applySeasonReset(seasonId));
    }

    /** 전원 지급이 확인된 뒤 시즌을 종료 처리한다(활성 시즌 패스 일괄 비활성 + processed 마킹). */
    @Transactional
    public void finalizeSeason(Long seasonId) {
        int deactivated = userSeasonPassRepository.deactivateAllActive();
        seasonRepository.findById(seasonId).ifPresent(Season::markProcessed);
        log.info("시즌 종료 확정. seasonId={}, 패스 일괄 종료={}", seasonId, deactivated);
    }

    // 시즌 보상 GP는 위치가 없으므로 금고로 적립한다.
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
}
