package com.territorial.auction.domain.ranking.service;

import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.ranking.dto.AuctionSpendRankingResponse;
import com.territorial.auction.domain.ranking.dto.AuctionSpendRankingResponse.RankEntry;
import com.territorial.auction.domain.ranking.dto.ContinentRankingResponse;
import com.territorial.auction.domain.ranking.dto.MyRankingResponse;
import com.territorial.auction.domain.ranking.dto.MyRankingResponse.AuctionSpendSummary;
import com.territorial.auction.domain.ranking.dto.MyRankingResponse.TerritoryHoldSummary;
import com.territorial.auction.domain.ranking.dto.TerritoryHoldRankingResponse;
import com.territorial.auction.domain.ranking.dto.TrophyRankingResponse;
import com.territorial.auction.domain.ranking.entity.SeasonTerritoryHold;
import com.territorial.auction.domain.ranking.event.AuctionSettledEvent;
import com.territorial.auction.domain.ranking.event.TerritoryHoldClosedEvent;
import com.territorial.auction.domain.ranking.event.TerritoryHoldStartedEvent;
import com.territorial.auction.domain.ranking.repository.SeasonTerritoryHoldRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.client.SeasonQueryClient;
import com.territorial.auction.global.client.SeasonQueryClient.ActiveSeason;
import com.territorial.auction.global.client.SeasonTrophyClient;
import com.territorial.auction.global.client.SeasonTrophyClient.Trophy;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final String TERRITORY_HOLD_KEY = "ranking:season:%d:territory_hold";
    private static final String AUCTION_SPEND_KEY = "ranking:season:%d:auction_spend";
    private static final String TERRITORY_HOLD_UPDATED_AT_KEY =
            "ranking:season:%d:territory_hold:updated_at";
    private static final int MAX_SIZE = 100;

    private static final Map<String, Integer> GRADE_WEIGHT =
            Map.of("S", 5, "A", 4, "B", 3, "C", 2, "D", 1);

    private final SeasonTerritoryHoldRepository seasonTerritoryHoldRepository;
    private final SeasonQueryClient seasonQueryClient;
    private final TerritoryRepository territoryRepository;
    private final ContinentRepository continentRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final SeasonTrophyClient seasonTrophyClient;

    @Cacheable(
            value = "ranking",
            key = "'territory-hold:p' + #page + ':s' + #size + ':u' + #userId")
    public TerritoryHoldRankingResponse getTerritoryHoldRanking(Long userId, int page, int size) {
        int effectiveSize = Math.min(size, MAX_SIZE);
        Optional<ActiveSeason> seasonOpt = seasonQueryClient.getActiveSeason();
        if (seasonOpt.isEmpty()) {
            return new TerritoryHoldRankingResponse(
                    null, null, "TERRITORY_HOLD", List.of(), null, null, null);
        }
        ActiveSeason season = seasonOpt.get();
        String key = String.format(TERRITORY_HOLD_KEY, season.seasonId());
        LocalDateTime updatedAt = parseUpdatedAt(season.seasonId());

        long start = (long) page * effectiveSize;
        long stop = start + effectiveSize - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, stop);

        List<SeasonTerritoryHold> allHolds =
                seasonTerritoryHoldRepository.findAllBySeasonId(season.seasonId());
        List<TerritoryHoldRankingResponse.RankEntry> rankings =
                buildTerritoryHoldEntries(tuples, allHolds, start);
        Integer myRank = userId != null ? findMyRank(key, String.valueOf(userId)) : null;
        Long myScore = userId != null ? getMyScore(key, String.valueOf(userId)) : null;

        return new TerritoryHoldRankingResponse(
                season.seasonId(),
                season.seasonNumber(),
                "TERRITORY_HOLD",
                rankings,
                myRank,
                myScore,
                updatedAt);
    }

    @Cacheable(value = "ranking", key = "'auction-spend:p' + #page + ':s' + #size + ':u' + #userId")
    public AuctionSpendRankingResponse getAuctionSpendRanking(Long userId, int page, int size) {
        int effectiveSize = Math.min(size, MAX_SIZE);
        Optional<ActiveSeason> seasonOpt = seasonQueryClient.getActiveSeason();
        if (seasonOpt.isEmpty()) {
            return new AuctionSpendRankingResponse(
                    null, null, "AUCTION_SPEND", List.of(), null, null, LocalDateTime.now());
        }
        ActiveSeason season = seasonOpt.get();
        String key = String.format(AUCTION_SPEND_KEY, season.seasonId());

        long start = (long) page * effectiveSize;
        long stop = start + effectiveSize - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, stop);

        List<RankEntry> rankings = buildAuctionSpendEntries(tuples, start);
        Integer myRank = userId != null ? findMyRank(key, String.valueOf(userId)) : null;
        Long myScore = userId != null ? getMyScore(key, String.valueOf(userId)) : null;

        return new AuctionSpendRankingResponse(
                season.seasonId(),
                season.seasonNumber(),
                "AUCTION_SPEND",
                rankings,
                myRank,
                myScore,
                LocalDateTime.now());
    }

    // 트로피는 user_trophies 테이블 기준(유저당 1행, 시즌 간 소프트 리셋)으로 직접 조회한다.
    // 트로피 변동 시점에 캐시를 무효화할 트리거가 없어 @Cacheable은 적용하지 않는다.
    public TrophyRankingResponse getTrophyRanking(Long userId, int page, int size) {
        int effectiveSize = Math.min(size, MAX_SIZE);
        Optional<ActiveSeason> seasonOpt = seasonQueryClient.getActiveSeason();
        Long seasonId = seasonOpt.map(ActiveSeason::seasonId).orElse(null);
        Integer seasonNumber = seasonOpt.map(ActiveSeason::seasonNumber).orElse(null);

        List<Trophy> trophies = seasonTrophyClient.getRanking(page, effectiveSize);
        Map<Long, String> nicknameByUser =
                batchLoadNicknames(trophies.stream().map(Trophy::userId).toList());
        long start = (long) page * effectiveSize;
        List<TrophyRankingResponse.RankEntry> rankings = new ArrayList<>();
        int index = 0;
        for (Trophy trophy : trophies) {
            rankings.add(
                    new TrophyRankingResponse.RankEntry(
                            (int) (start + index + 1),
                            trophy.userId(),
                            nicknameByUser.getOrDefault(trophy.userId(), "알 수 없음"),
                            trophy.score(),
                            trophy.league()));
            index++;
        }

        Integer myRank = null;
        Long myScore = null;
        String myLeague = null;
        if (userId != null) {
            Trophy mine = seasonTrophyClient.getTrophy(userId).orElse(null);
            if (mine != null) {
                myScore = (long) mine.score();
                myLeague = mine.league();
                myRank = (int) seasonTrophyClient.countAbove(mine.score()) + 1;
            }
        }

        return new TrophyRankingResponse(
                seasonId,
                seasonNumber,
                "TROPHY",
                rankings,
                myRank,
                myScore,
                myLeague,
                LocalDateTime.now());
    }

    // 대륙은 입장 트로피 기준의 티어다. 따라서 대륙 랭킹은 해당 대륙의 트로피 밴드
    // [이 대륙 minTrophyRequired, 다음 등급 대륙 minTrophyRequired) 안에 드는
    // 유저들의 트로피 점수 순위로 출력한다.
    public ContinentRankingResponse getContinentRanking(
            Long userId, Long continentId, int page, int size) {
        Continent continent = findContinentOrThrow(continentId);
        int lower = continent.getMinTrophyRequired() != null ? continent.getMinTrophyRequired() : 0;
        int upper = resolveUpperBound(lower);

        int effectiveSize = Math.min(size, MAX_SIZE);
        List<Trophy> trophies = seasonTrophyClient.getBand(lower, upper, page, effectiveSize);
        Trophy myTrophy = userId != null ? seasonTrophyClient.getTrophy(userId).orElse(null) : null;
        Optional<ActiveSeason> seasonOpt = seasonQueryClient.getActiveSeason();

        return new ContinentRankingResponse(
                continentId,
                seasonOpt.map(ActiveSeason::seasonId).orElse(null),
                seasonOpt.map(ActiveSeason::seasonNumber).orElse(null),
                "CONTINENT_TROPHY",
                buildBandTrophyEntries(trophies, (long) page * effectiveSize),
                calculateBandRank(myTrophy, lower, upper),
                bandScore(myTrophy, lower, upper),
                LocalDateTime.now());
    }

    @Cacheable(value = "ranking", key = "'my:u' + #userId")
    public MyRankingResponse getMyRanking(Long userId) {
        Optional<ActiveSeason> seasonOpt = seasonQueryClient.getActiveSeason();
        if (seasonOpt.isEmpty()) {
            return new MyRankingResponse(null, null, null, null);
        }
        ActiveSeason season = seasonOpt.get();
        String holdKey = String.format(TERRITORY_HOLD_KEY, season.seasonId());
        String spendKey = String.format(AUCTION_SPEND_KEY, season.seasonId());
        String userIdStr = String.valueOf(userId);

        Integer holdRank = findMyRank(holdKey, userIdStr);
        Long holdScore = getMyScore(holdKey, userIdStr);
        Map<String, Long> gradeBreakdown = buildGradeBreakdownForUser(season.seasonId(), userId);

        Integer spendRank = findMyRank(spendKey, userIdStr);
        Long spendScore = getMyScore(spendKey, userIdStr);

        return new MyRankingResponse(
                season.seasonId(),
                season.seasonNumber(),
                new TerritoryHoldSummary(holdRank, holdScore, gradeBreakdown),
                new AuctionSpendSummary(spendRank, spendScore));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handleAuctionSettled(AuctionSettledEvent event) {
        try {
            String key = String.format(AUCTION_SPEND_KEY, event.seasonId());
            stringRedisTemplate
                    .opsForZSet()
                    .incrementScore(key, String.valueOf(event.userId()), event.finalPrice());
            log.info(
                    "경매 소비 랭킹 업데이트. userId={}, seasonId={}, price={}",
                    event.userId(),
                    event.seasonId(),
                    event.finalPrice());
        } catch (Exception e) {
            log.error("랭킹 이벤트 처리 실패. event={}", event, e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTerritoryHoldStarted(TerritoryHoldStartedEvent event) {
        try {
            User user =
                    userRepository
                            .findById(event.userId())
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            Territory territory =
                    territoryRepository
                            .findById(event.territoryId())
                            .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));

            seasonTerritoryHoldRepository.save(
                    SeasonTerritoryHold.builder()
                            .seasonId(event.seasonId())
                            .user(user)
                            .territory(territory)
                            .grade(event.grade())
                            .heldFrom(event.heldFrom())
                            .build());
            log.info(
                    "영토 점유 시작 기록. userId={}, seasonId={}, territoryId={}",
                    event.userId(),
                    event.seasonId(),
                    event.territoryId());
        } catch (Exception e) {
            log.error("랭킹 이벤트 처리 실패. event={}", event, e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTerritoryHoldClosed(TerritoryHoldClosedEvent event) {
        try {
            seasonTerritoryHoldRepository
                    .findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(
                            event.seasonId(), event.userId(), event.territoryId())
                    .ifPresent(hold -> hold.closeHold(event.heldUntil()));
            log.info(
                    "영토 점유 종료 기록. userId={}, seasonId={}, territoryId={}",
                    event.userId(),
                    event.seasonId(),
                    event.territoryId());
        } catch (Exception e) {
            log.error("랭킹 이벤트 처리 실패. event={}", event, e);
        }
    }

    @CacheEvict(value = "ranking", allEntries = true)
    public void aggregateTerritoryHoldRanking(Long seasonId) {
        List<SeasonTerritoryHold> holds = seasonTerritoryHoldRepository.findAllBySeasonId(seasonId);
        Map<Long, Long> scoreByUser = calculateScoresByUser(holds);

        String key = String.format(TERRITORY_HOLD_KEY, seasonId);
        stringRedisTemplate.delete(key);
        scoreByUser.forEach(
                (uid, score) ->
                        stringRedisTemplate.opsForZSet().add(key, String.valueOf(uid), score));

        String updatedAtKey = String.format(TERRITORY_HOLD_UPDATED_AT_KEY, seasonId);
        stringRedisTemplate.opsForValue().set(updatedAtKey, LocalDateTime.now().toString());
        log.info("영토 점유 랭킹 집계 완료. seasonId={}, userCount={}", seasonId, scoreByUser.size());
    }

    // ── private ───────────────────────────────────────────────────────────────

    private LocalDateTime parseUpdatedAt(Long seasonId) {
        String raw =
                stringRedisTemplate
                        .opsForValue()
                        .get(String.format(TERRITORY_HOLD_UPDATED_AT_KEY, seasonId));
        return raw != null ? LocalDateTime.parse(raw) : null;
    }

    private Integer findMyRank(String key, String userIdStr) {
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, userIdStr);
        return rank != null ? (int) (rank + 1) : null;
    }

    private Long getMyScore(String key, String userIdStr) {
        Double score = stringRedisTemplate.opsForZSet().score(key, userIdStr);
        return score != null ? score.longValue() : null;
    }

    private Continent findContinentOrThrow(Long continentId) {
        return continentRepository
                .findById(continentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTINENT_NOT_FOUND));
    }

    private int resolveUpperBound(int lower) {
        Integer nextMin = continentRepository.findNextMinTrophyAbove(lower);
        return nextMin != null ? nextMin : Integer.MAX_VALUE;
    }

    private List<ContinentRankingResponse.RankEntry> buildBandTrophyEntries(
            List<Trophy> trophies, long start) {
        Map<Long, String> nicknameByUser =
                batchLoadNicknames(trophies.stream().map(Trophy::userId).toList());
        List<ContinentRankingResponse.RankEntry> entries = new ArrayList<>();
        int index = 0;
        for (Trophy trophy : trophies) {
            entries.add(
                    new ContinentRankingResponse.RankEntry(
                            (int) (start + index + 1),
                            trophy.userId(),
                            nicknameByUser.getOrDefault(trophy.userId(), "알 수 없음"),
                            trophy.score()));
            index++;
        }
        return entries;
    }

    private boolean isInBand(Trophy trophy, int lower, int upper) {
        return trophy != null && trophy.score() >= lower && trophy.score() < upper;
    }

    private Integer calculateBandRank(Trophy trophy, int lower, int upper) {
        if (!isInBand(trophy, lower, upper)) {
            return null;
        }
        return (int) seasonTrophyClient.countBand(trophy.score(), upper) + 1;
    }

    private Long bandScore(Trophy trophy, int lower, int upper) {
        return isInBand(trophy, lower, upper) ? (long) trophy.score() : null;
    }

    private Map<Long, Long> calculateScoresByUser(List<SeasonTerritoryHold> holds) {
        Map<Long, Long> scoreByUser = new HashMap<>();
        for (SeasonTerritoryHold hold : holds) {
            long score = calculateHoldScore(hold);
            scoreByUser.merge(hold.getUser().getId(), score, Long::sum);
        }
        return scoreByUser;
    }

    private long calculateHoldScore(SeasonTerritoryHold hold) {
        LocalDateTime until =
                hold.getHeldUntil() != null ? hold.getHeldUntil() : LocalDateTime.now();
        long seconds = Duration.between(hold.getHeldFrom(), until).getSeconds();
        return seconds * gradeWeight(hold.getGrade());
    }

    private int gradeWeight(String grade) {
        return GRADE_WEIGHT.getOrDefault(grade, 0);
    }

    private List<TerritoryHoldRankingResponse.RankEntry> buildTerritoryHoldEntries(
            Set<ZSetOperations.TypedTuple<String>> tuples,
            List<SeasonTerritoryHold> allHolds,
            long start) {
        if (tuples == null) return List.of();

        List<Long> uids =
                tuples.stream().map(t -> Long.parseLong(t.getValue())).collect(Collectors.toList());

        Map<Long, String> nicknameByUser = batchLoadNicknames(uids);
        Map<Long, Map<String, Long>> breakdownByUser = buildBreakdownByUser(allHolds);

        List<TerritoryHoldRankingResponse.RankEntry> entries = new ArrayList<>();
        int index = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long uid = Long.parseLong(tuple.getValue());
            long score = tuple.getScore() != null ? tuple.getScore().longValue() : 0L;
            String nickname = nicknameByUser.getOrDefault(uid, "알 수 없음");
            Map<String, Long> breakdown = breakdownByUser.getOrDefault(uid, Map.of());
            entries.add(
                    new TerritoryHoldRankingResponse.RankEntry(
                            (int) (start + index + 1), uid, nickname, score, breakdown));
            index++;
        }
        return entries;
    }

    private List<RankEntry> buildAuctionSpendEntries(
            Set<ZSetOperations.TypedTuple<String>> tuples, long start) {
        if (tuples == null) return List.of();

        List<Long> uids =
                tuples.stream().map(t -> Long.parseLong(t.getValue())).collect(Collectors.toList());

        Map<Long, String> nicknameByUser = batchLoadNicknames(uids);

        List<RankEntry> entries = new ArrayList<>();
        int index = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long uid = Long.parseLong(tuple.getValue());
            long score = tuple.getScore() != null ? tuple.getScore().longValue() : 0L;
            String nickname = nicknameByUser.getOrDefault(uid, "알 수 없음");
            entries.add(new RankEntry((int) (start + index + 1), uid, nickname, score));
            index++;
        }
        return entries;
    }

    private Map<Long, String> batchLoadNicknames(List<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    private Map<Long, Map<String, Long>> buildBreakdownByUser(List<SeasonTerritoryHold> holds) {
        Map<Long, Map<String, Long>> result = new HashMap<>();
        for (SeasonTerritoryHold hold : holds) {
            Long uid = hold.getUser().getId();
            LocalDateTime until =
                    hold.getHeldUntil() != null ? hold.getHeldUntil() : LocalDateTime.now();
            long seconds = Duration.between(hold.getHeldFrom(), until).getSeconds();
            result.computeIfAbsent(uid, k -> new HashMap<>())
                    .merge(hold.getGrade(), seconds, Long::sum);
        }
        return result;
    }

    private Map<String, Long> buildGradeBreakdownForUser(Long seasonId, Long userId) {
        List<SeasonTerritoryHold> holds =
                seasonTerritoryHoldRepository.findBySeasonIdAndUserId(seasonId, userId);
        Map<String, Long> breakdown = new HashMap<>();
        for (SeasonTerritoryHold hold : holds) {
            LocalDateTime until =
                    hold.getHeldUntil() != null ? hold.getHeldUntil() : LocalDateTime.now();
            long seconds = Duration.between(hold.getHeldFrom(), until).getSeconds();
            breakdown.merge(hold.getGrade(), seconds, Long::sum);
        }
        return breakdown;
    }
}
