package com.territorial.ranking.domain.ranking.service;

import com.territorial.ranking.client.ContinentBandClient;
import com.territorial.ranking.client.ContinentBandClient.TrophyBand;
import com.territorial.ranking.client.NicknameClient;
import com.territorial.ranking.client.SeasonGameEventClient;
import com.territorial.ranking.client.SeasonQueryClient;
import com.territorial.ranking.client.SeasonQueryClient.ActiveSeason;
import com.territorial.ranking.client.SeasonTrophyClient;
import com.territorial.ranking.client.SeasonTrophyClient.Trophy;
import com.territorial.ranking.domain.ranking.dto.AuctionSpendRankingResponse;
import com.territorial.ranking.domain.ranking.dto.AuctionSpendRankingResponse.RankEntry;
import com.territorial.ranking.domain.ranking.dto.ContinentRankingResponse;
import com.territorial.ranking.domain.ranking.dto.MyRankingResponse;
import com.territorial.ranking.domain.ranking.dto.MyRankingResponse.AuctionSpendSummary;
import com.territorial.ranking.domain.ranking.dto.MyRankingResponse.TerritoryHoldSummary;
import com.territorial.ranking.domain.ranking.dto.TerritoryHoldRankingResponse;
import com.territorial.ranking.domain.ranking.dto.TrophyRankingResponse;
import com.territorial.ranking.domain.ranking.entity.SeasonTerritoryHold;
import com.territorial.ranking.domain.ranking.repository.SeasonTerritoryHoldRepository;
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
import org.springframework.transaction.annotation.Transactional;

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
    private final SeasonTrophyClient seasonTrophyClient;
    private final SeasonGameEventClient seasonGameEventClient;
    private final ContinentBandClient continentBandClient;
    private final NicknameClient nicknameClient;
    private final StringRedisTemplate stringRedisTemplate;

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

    // 트로피는 season-service(user_trophies)가 소유한다. 트로피 변동 시 캐시 무효화 트리거가 없어 @Cacheable 미적용.
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

    // 대륙 랭킹 = 해당 대륙의 트로피 밴드 [minTrophyRequired, 다음 등급 minTrophyRequired) 안 유저들의 점수 순위.
    // 밴드 경계는 정적 config인 대륙을 소유한 모놀리식 map에서 받는다.
    public ContinentRankingResponse getContinentRanking(
            Long userId, Long continentId, int page, int size) {
        TrophyBand band = continentBandClient.getTrophyBand(continentId);
        int lower = band.lower();
        int upper = band.upper();

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

    // ── 쓰기(이벤트 위임) ─────────────────────────────────────────────────────

    /**
     * 경매 낙찰(auction.settled) 처리. XP·미션은 season-service에 위임하고, 활성 시즌이면 경매소비 랭킹 증분 + 영토점유 시작을 기록한다.
     * 활성 시즌 판단은 여기서 하되 XP는 season-service가 자체 재판단한다(best-effort).
     */
    // 클래스 기본 readOnly를 덮어 쓰기 트랜잭션으로 연다 — 내부에서 hold INSERT가 일어난다.
    // hold 저장을 먼저 하고 ZSet 증분을 나중에 해, 저장 실패 시 재시도에서 ZSet 중복 증분을 줄인다.
    @Transactional
    public void onAuctionSettled(Long winnerId, Long territoryId, String grade, int finalPrice) {
        seasonGameEventClient.sendGameEvent(winnerId, "AUCTION_WIN");

        Long seasonId =
                seasonQueryClient.getActiveSeason().map(ActiveSeason::seasonId).orElse(null);
        if (seasonId == null) {
            return; // 시즌 외 기간엔 랭킹·시즌 귀속 없음
        }
        recordTerritoryHoldStarted(winnerId, seasonId, territoryId, grade, LocalDateTime.now());
        recordAuctionSpend(winnerId, seasonId, finalPrice);
    }

    private void recordAuctionSpend(Long userId, Long seasonId, int finalPrice) {
        String key = String.format(AUCTION_SPEND_KEY, seasonId);
        stringRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(userId), finalPrice);
        log.info("경매 소비 랭킹 업데이트. userId={}, seasonId={}, price={}", userId, seasonId, finalPrice);
    }

    @Transactional
    public void recordTerritoryHoldStarted(
            Long userId, Long seasonId, Long territoryId, String grade, LocalDateTime heldFrom) {
        seasonTerritoryHoldRepository.save(
                SeasonTerritoryHold.builder()
                        .seasonId(seasonId)
                        .userId(userId)
                        .territoryId(territoryId)
                        .grade(grade)
                        .heldFrom(heldFrom)
                        .build());
        log.info(
                "영토 점유 시작 기록. userId={}, seasonId={}, territoryId={}",
                userId,
                seasonId,
                territoryId);
    }

    /** 영토 점유 종료 — map(모놀리식)이 점유 만료 시 위임 호출한다. 열린 기록이 없으면 조용히 종료. */
    @Transactional
    public void closeTerritoryHold(
            Long userId, Long seasonId, Long territoryId, LocalDateTime heldUntil) {
        seasonTerritoryHoldRepository
                .findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(
                        seasonId, userId, territoryId)
                .ifPresent(hold -> hold.closeHold(heldUntil));
        log.info(
                "영토 점유 종료 기록. userId={}, seasonId={}, territoryId={}",
                userId,
                seasonId,
                territoryId);
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
            scoreByUser.merge(hold.getUserId(), score, Long::sum);
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
        return nicknameClient.getNicknames(userIds);
    }

    private Map<Long, Map<String, Long>> buildBreakdownByUser(List<SeasonTerritoryHold> holds) {
        Map<Long, Map<String, Long>> result = new HashMap<>();
        for (SeasonTerritoryHold hold : holds) {
            Long uid = hold.getUserId();
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
