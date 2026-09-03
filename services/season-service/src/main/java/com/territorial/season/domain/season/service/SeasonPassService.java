package com.territorial.season.domain.season.service;

import com.territorial.season.client.CombatResourceClient;
import com.territorial.season.domain.season.SeasonPassPolicy;
import com.territorial.season.client.ItemGrantClient;
import com.territorial.season.domain.season.dto.ClaimRewardResponse;
import com.territorial.season.domain.season.dto.CombatSeasonBenefitResponse;
import com.territorial.season.domain.season.dto.MySeasonPassResponse;
import com.territorial.season.domain.season.dto.PurchaseLevelResponse;
import com.territorial.season.domain.season.dto.PurchaseSeasonPassResponse;
import com.territorial.season.domain.season.dto.SeasonPassResponse;
import com.territorial.season.domain.season.entity.RewardItemType;
import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.SeasonPass;
import com.territorial.season.domain.season.entity.SeasonPassLevelReward;
import com.territorial.season.domain.season.entity.SeasonPassProgress;
import com.territorial.season.domain.season.entity.SeasonPassRewardClaim;
import com.territorial.season.domain.season.entity.UserSeasonPass;
import com.territorial.season.domain.season.repository.SeasonPassLevelRewardRepository;
import com.territorial.season.domain.season.repository.SeasonPassProgressRepository;
import com.territorial.season.domain.season.repository.SeasonPassRepository;
import com.territorial.season.domain.season.repository.SeasonPassRewardClaimRepository;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.UserSeasonPassRepository;
import com.territorial.season.client.WalletClient;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.season.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonPassService {

    private static final String CACHE_MY_PASS = "season_pass:my:";
    private static final String CACHE_PROGRESS = "season_pass:progress:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int XP_PER_LEVEL = 1000;

    private final SeasonRepository seasonRepository;
    private final SeasonPassRepository seasonPassRepository;
    private final UserSeasonPassRepository userSeasonPassRepository;
    private final SeasonPassProgressRepository seasonPassProgressRepository;
    private final SeasonPassLevelRewardRepository seasonPassLevelRewardRepository;
    private final SeasonPassRewardClaimRepository seasonPassRewardClaimRepository;    private final WalletClient walletClient;
    private final CombatResourceClient combatResourceClient;
    private final ItemGrantClient itemGrantClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public CombatSeasonBenefitResponse getCombatBenefit(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return userSeasonPassRepository
                .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId)
                .filter(pass -> pass.getExpiresAt().isAfter(now))
                .map(
                        pass ->
                                new CombatSeasonBenefitResponse(
                                        pass.getSeasonPass().getBuildTimeReductionPct(),
                                        pass.getSeasonPass().getExtraBuilders()))
                .orElseGet(CombatSeasonBenefitResponse::none);
    }

    public SeasonPassResponse getProgress(Long userId) {
        try {
            Object cached = redisTemplate.opsForValue().get(CACHE_PROGRESS + userId);
            if (cached instanceof SeasonPassResponse response) {
                return response;
            }
        } catch (Exception e) {
            log.warn("시즌 패스 현황 Redis 캐시 조회 실패 - userId: {}", userId, e);
        }

        Season season =
                seasonRepository
                        .findActiveSeason(LocalDateTime.now())
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_NOT_FOUND));

        boolean hasPremiumPass =
                userSeasonPassRepository
                        .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId)
                        .filter(pass -> pass.getExpiresAt().isAfter(LocalDateTime.now()))
                        .isPresent();

        SeasonPassProgress progress =
                seasonPassProgressRepository
                        .findByUserIdAndSeason_Id(userId, season.getId())
                        .orElse(null);

        int currentLevel = progress != null ? progress.getLevel() : 1;
        int currentXp = progress != null ? progress.getXp() : 0;

        List<SeasonPassLevelReward> allRewards =
                seasonPassLevelRewardRepository.findBySeason_IdOrderByLevelAsc(season.getId());
        Set<Long> claimedIds =
                seasonPassRewardClaimRepository.findClaimedRewardIdsByUserIdAndSeasonId(
                        userId, season.getId());

        List<SeasonPassResponse.RewardItem> rewardItems =
                allRewards.stream()
                        .map(
                                r -> {
                                    boolean isClaimed = claimedIds.contains(r.getId());
                                    boolean isPremium =
                                            r.getTrack()
                                                    == SeasonPassLevelReward.RewardTrack.PREMIUM;
                                    boolean canClaim =
                                            currentLevel >= r.getLevel()
                                                    && (!isPremium || hasPremiumPass)
                                                    && !isClaimed;
                                    return new SeasonPassResponse.RewardItem(
                                            r.getId(),
                                            r.getLevel(),
                                            r.getTrack().name(),
                                            r.getRewardName(),
                                            isClaimed,
                                            canClaim);
                                })
                        .toList();

        int passCostAp =
                seasonPassRepository
                        .findFirstByOrderByIdDesc()
                        .map(SeasonPass::getCostAp)
                        .orElse(0);

        SeasonPassResponse response =
                new SeasonPassResponse(
                        season.getId(),
                        "Season " + season.getSeasonNumber(),
                        hasPremiumPass ? "PREMIUM" : "FREE",
                        currentLevel,
                        currentXp,
                        XP_PER_LEVEL,
                        passCostAp,
                        SeasonPassPolicy.LEVEL_UP_COST_AP,
                        season.getEndedAt(),
                        rewardItems);

        try {
            redisTemplate.opsForValue().set(CACHE_PROGRESS + userId, response, CACHE_TTL);
        } catch (Exception e) {
            log.warn("시즌 패스 현황 Redis 캐시 저장 실패 - userId: {}", userId, e);
        }
        return response;
    }

    public MySeasonPassResponse getMyPass(Long userId) {
        try {
            Object cached = redisTemplate.opsForValue().get(CACHE_MY_PASS + userId);
            if (cached instanceof MySeasonPassResponse response) {
                return response;
            }
        } catch (Exception e) {
            log.warn("시즌 패스 보유 Redis 캐시 조회 실패 - userId: {}", userId, e);
        }

        MySeasonPassResponse response =
                userSeasonPassRepository
                        .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId)
                        .filter(pass -> pass.getExpiresAt().isAfter(LocalDateTime.now()))
                        .map(MySeasonPassResponse::from)
                        .orElse(new MySeasonPassResponse(false, null));

        try {
            redisTemplate.opsForValue().set(CACHE_MY_PASS + userId, response, CACHE_TTL);
        } catch (Exception e) {
            log.warn("시즌 패스 보유 Redis 캐시 저장 실패 - userId: {}", userId, e);
        }
        return response;
    }

    @Transactional
    public PurchaseSeasonPassResponse purchase(Long userId) {
        SeasonPass pass =
                seasonPassRepository
                        .findFirstByOrderByIdDesc()
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_PASS_NOT_FOUND));

        boolean alreadyOwns =
                userSeasonPassRepository
                        .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId)
                        .filter(p -> p.getExpiresAt().isAfter(LocalDateTime.now()))
                        .isPresent();
        if (alreadyOwns) {
            throw new CustomException(ErrorCode.SEASON_PASS_ALREADY_OWNED);
        }

        Season season =
                seasonRepository
                        .findActiveSeason(LocalDateTime.now())
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        // 패스는 현재 시즌에 종속 — 만료는 시즌 종료 시각. 시즌 종료 배치가 일괄 비활성화한다.
        LocalDateTime expiresAt =
                season.getEndedAt() != null ? season.getEndedAt() : now.plusDays(30);
        UserSeasonPass userPass =
                userSeasonPassRepository.save(
                        UserSeasonPass.builder()
                                .userId(userId)
                                .seasonPass(pass)
                                .startedAt(now)
                                .expiresAt(expiresAt)
                                .build());

        // 로컬 저장 후 마지막에 AP 소비 — 실패 시 롤백으로 패스 지급도 취소(정합)
        var wallet =
                walletClient.spend(
                        userId, pass.getCostAp(), "SEASON_PASS:" + userId + ":" + season.getId());

        try {
            redisTemplate
                    .opsForValue()
                    .set(CACHE_MY_PASS + userId, MySeasonPassResponse.from(userPass), CACHE_TTL);
            redisTemplate.delete(CACHE_PROGRESS + userId);
        } catch (Exception e) {
            log.warn("시즌 패스 구매 후 Redis 캐시 갱신 실패 - userId: {}", userId, e);
            redisTemplate.delete(CACHE_MY_PASS + userId);
            redisTemplate.delete(CACHE_PROGRESS + userId);
        }

        return PurchaseSeasonPassResponse.of(userPass, wallet.availableAp());
    }

    @Transactional
    public PurchaseLevelResponse purchaseLevel(Long userId) {
        Season season =
                seasonRepository
                        .findActiveSeason(LocalDateTime.now())
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_NOT_FOUND));
        SeasonPassProgress progress = findOrCreateProgress(userId, season);
        if (progress.getLevel() >= SeasonPassPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.SEASON_LEVEL_MAX_REACHED);
        }

        int cost = SeasonPassPolicy.LEVEL_UP_COST_AP;
        progress.levelUpByPurchase();
        invalidateProgressCache(userId);
        var wallet =
                walletClient.spend(
                        userId, cost, "SEASON_LEVEL:" + userId + ":" + progress.getLevel());

        log.info(
                "시즌 패스 레벨 구매. userId={}, newLevel={}, costAp={}",
                userId,
                progress.getLevel(),
                cost);
        return new PurchaseLevelResponse(
                progress.getLevel(), progress.getXp(), cost, wallet.availableAp());
    }

    private SeasonPassProgress findOrCreateProgress(Long userId, Season season) {
        return seasonPassProgressRepository
                .findByUserIdAndSeason_Id(userId, season.getId())
                .orElseGet(
                        () ->
                                seasonPassProgressRepository.save(
                                        SeasonPassProgress.builder()
                                                .userId(userId)
                                                .season(season)
                                                .build()));
    }

    private void invalidateProgressCache(Long userId) {
        try {
            redisTemplate.delete(CACHE_PROGRESS + userId);
        } catch (Exception e) {
            log.warn("시즌 패스 진행도 캐시 무효화 실패. userId={}", userId);
        }
    }

    @Transactional
    public ClaimRewardResponse claimReward(Long userId, Long rewardId) {
        SeasonPassLevelReward reward =
                seasonPassLevelRewardRepository
                        .findById(rewardId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_REWARD_NOT_FOUND));

        SeasonPassProgress progress =
                seasonPassProgressRepository
                        .findByUserIdAndSeason_Id(userId, reward.getSeason().getId())
                        .orElse(null);
        int currentLevel = progress != null ? progress.getLevel() : 1;
        if (currentLevel < reward.getLevel()) {
            throw new CustomException(ErrorCode.REWARD_LEVEL_NOT_REACHED);
        }

        if (reward.getTrack() == SeasonPassLevelReward.RewardTrack.PREMIUM) {
            boolean hasPremiumPass =
                    userSeasonPassRepository
                            .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId)
                            .filter(p -> p.getExpiresAt().isAfter(LocalDateTime.now()))
                            .isPresent();
            if (!hasPremiumPass) {
                throw new CustomException(ErrorCode.REWARD_PREMIUM_REQUIRED);
            }
        }

        if (seasonPassRewardClaimRepository.existsByUserIdAndReward_Id(userId, rewardId)) {
            throw new CustomException(ErrorCode.REWARD_ALREADY_CLAIMED);
        }
        seasonPassRewardClaimRepository.save(
                SeasonPassRewardClaim.builder().userId(userId).reward(reward).build());

        grantReward(userId, reward);
        invalidateProgressCache(userId);

        return new ClaimRewardResponse(
                reward.getId(),
                reward.getRewardName(),
                reward.getTrack().name(),
                LocalDateTime.now());
    }

    private void grantReward(Long userId, SeasonPassLevelReward reward) {
        switch (reward.getRewardKind()) {
            case GP ->
                    grantGp(
                            userId,
                            reward.getQuantity(),
                            "SEASON_PASS_REWARD:" + userId + ":" + reward.getId());
            case ITEM -> grantItem(userId, reward.getItemType(), reward.getQuantity());
            case BUILD_TIME_REDUCTION ->
                    grantBuildTimeReduction(userId, reward.getQuantity());
        }
        log.info(
                "시즌 패스 보상 지급. userId={}, kind={}, reward={}",
                userId,
                reward.getRewardKind(),
                reward.getRewardName());
    }

    // PREMIUM 트랙 보상이라 활성 패스가 반드시 존재한다.
    private void grantBuildTimeReduction(Long userId, int pct) {
        UserSeasonPass userSeasonPass =
                userSeasonPassRepository
                        .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_PASS_NOT_FOUND));
        userSeasonPass.addBuildTimeReduction(pct);
    }

    // 시즌패스 보상 GP는 위치가 없으므로 금고로 적립한다. 금고가 없으면 만든다.
    private void grantGp(Long userId, int amount, String commandKey) {
        if (amount <= 0) return;
        combatResourceClient.creditGp(userId, amount, commandKey);
    }

    private void grantItem(Long userId, RewardItemType itemType, int quantity) {
        itemGrantClient.grantByType(userId, itemType.name(), quantity);
    }
}
