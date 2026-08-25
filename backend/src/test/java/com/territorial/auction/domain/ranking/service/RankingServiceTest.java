package com.territorial.auction.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.ranking.dto.AuctionSpendRankingResponse;
import com.territorial.auction.domain.ranking.dto.ContinentRankingResponse;
import com.territorial.auction.domain.ranking.dto.MyRankingResponse;
import com.territorial.auction.domain.ranking.dto.TerritoryHoldRankingResponse;
import com.territorial.auction.domain.ranking.dto.TrophyRankingResponse;
import com.territorial.auction.domain.ranking.entity.SeasonTerritoryHold;
import com.territorial.auction.domain.ranking.event.AuctionSettledEvent;
import com.territorial.auction.domain.ranking.event.TerritoryHoldClosedEvent;
import com.territorial.auction.domain.ranking.event.TerritoryHoldStartedEvent;
import com.territorial.auction.domain.ranking.repository.SeasonTerritoryHoldRepository;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.entity.UserTrophy;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.season.repository.UserTrophyRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @InjectMocks private RankingService rankingService;

    @Mock private SeasonTerritoryHoldRepository seasonTerritoryHoldRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private UserRepository userRepository;
    @Mock private UserTrophyRepository userTrophyRepository;
    @Mock private ContinentRepository continentRepository;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private ValueOperations<String, String> valueOperations;

    private Season season;
    private User user;
    private Territory territory;
    private SeasonTerritoryHold hold;

    @BeforeEach
    void setUp() {
        season =
                Season.builder()
                        .seasonNumber(1)
                        .startedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                        .endedAt(LocalDateTime.of(2026, 12, 31, 23, 59))
                        .build();
        ReflectionTestUtils.setField(season, "id", 1L);

        user =
                User.builder()
                        .username("testuser")
                        .email("test@example.com")
                        .passwordHash("hashed")
                        .nickname("테스터")
                        .build();
        ReflectionTestUtils.setField(user, "id", 10L);

        territory = Territory.builder().coordX(0).coordY(0).build();
        ReflectionTestUtils.setField(territory, "id", 100L);

        hold =
                SeasonTerritoryHold.builder()
                        .season(season)
                        .user(user)
                        .territory(territory)
                        .grade("S")
                        .heldFrom(LocalDateTime.of(2026, 5, 1, 0, 0))
                        .build();
        ReflectionTestUtils.setField(hold, "id", 1L);
    }

    @Nested
    @DisplayName("GetTerritoryHoldRanking")
    class GetTerritoryHoldRanking {

        @Test
        @DisplayName("시즌 존재 + Redis 데이터 있을 때 → rankings 반환")
        void success_withSeason() {
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);

            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            tuples.add(new TestTypedTuple("10", 5000.0));
            given(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(9L)))
                    .willReturn(tuples);
            given(zSetOperations.reverseRank(anyString(), eq("10"))).willReturn(0L);
            given(zSetOperations.score(anyString(), eq("10"))).willReturn(5000.0);
            given(userRepository.findAllById(any())).willReturn(List.of(user));
            given(seasonTerritoryHoldRepository.findAllBySeasonId(1L)).willReturn(List.of(hold));

            TerritoryHoldRankingResponse response =
                    rankingService.getTerritoryHoldRanking(10L, 0, 10);

            assertThat(response.seasonId()).isEqualTo(1L);
            assertThat(response.rankings()).hasSize(1);
            assertThat(response.rankings().get(0).rank()).isEqualTo(1);
            assertThat(response.rankings().get(0).userId()).isEqualTo(10L);
            assertThat(response.myRank()).isEqualTo(1);
        }

        @Test
        @DisplayName("활성 시즌 없을 때 → 빈 응답 반환")
        void success_noSeason() {
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.empty());

            TerritoryHoldRankingResponse response =
                    rankingService.getTerritoryHoldRanking(null, 0, 10);

            assertThat(response.seasonId()).isNull();
            assertThat(response.rankings()).isEmpty();
            assertThat(response.myRank()).isNull();
        }
    }

    @Nested
    @DisplayName("GetAuctionSpendRanking")
    class GetAuctionSpendRanking {

        @Test
        @DisplayName("시즌 존재 + Redis 데이터 있을 때 → rankings 반환")
        void success_withSeason() {
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);

            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            tuples.add(new TestTypedTuple("10", 3000.0));
            given(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(9L)))
                    .willReturn(tuples);
            given(zSetOperations.reverseRank(anyString(), eq("10"))).willReturn(0L);
            given(zSetOperations.score(anyString(), eq("10"))).willReturn(3000.0);
            given(userRepository.findAllById(any())).willReturn(List.of(user));

            AuctionSpendRankingResponse response =
                    rankingService.getAuctionSpendRanking(10L, 0, 10);

            assertThat(response.seasonId()).isEqualTo(1L);
            assertThat(response.rankings()).hasSize(1);
            assertThat(response.rankings().get(0).totalSpentAP()).isEqualTo(3000L);
            assertThat(response.myScore()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("활성 시즌 없을 때 → 빈 응답 반환")
        void success_noSeason() {
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.empty());

            AuctionSpendRankingResponse response =
                    rankingService.getAuctionSpendRanking(null, 0, 10);

            assertThat(response.seasonId()).isNull();
            assertThat(response.rankings()).isEmpty();
            assertThat(response.myRank()).isNull();
        }
    }

    @Nested
    @DisplayName("GetTrophyRanking")
    class GetTrophyRanking {

        private UserTrophy trophyWithScore(int score, UserTrophy.League league) {
            UserTrophy trophy = UserTrophy.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(trophy, "score", score);
            ReflectionTestUtils.setField(trophy, "league", league);
            return trophy;
        }

        @Test
        @DisplayName("트로피 보유 유저 존재 → 점수 내림차순 랭킹 + 내 순위 반환")
        void success_withTrophies() {
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));
            UserTrophy trophy = trophyWithScore(1200, UserTrophy.League.GOLD);
            given(userTrophyRepository.findAllByOrderByScoreDesc(any()))
                    .willReturn(new PageImpl<>(List.of(trophy)));
            given(userTrophyRepository.findById(10L)).willReturn(Optional.of(trophy));
            given(userTrophyRepository.countByScoreGreaterThan(1200)).willReturn(0L);

            TrophyRankingResponse response = rankingService.getTrophyRanking(10L, 0, 10);

            assertThat(response.rankings()).hasSize(1);
            assertThat(response.rankings().get(0).rank()).isEqualTo(1);
            assertThat(response.rankings().get(0).userId()).isEqualTo(10L);
            assertThat(response.rankings().get(0).score()).isEqualTo(1200);
            assertThat(response.rankings().get(0).league()).isEqualTo("GOLD");
            assertThat(response.myRank()).isEqualTo(1);
            assertThat(response.myScore()).isEqualTo(1200L);
            assertThat(response.myLeague()).isEqualTo("GOLD");
        }

        @Test
        @DisplayName("내 트로피 없음 → myRank null, 랭킹은 반환")
        void noMyTrophy() {
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllByOrderByScoreDesc(any()))
                    .willReturn(new PageImpl<>(List.of()));
            given(userTrophyRepository.findById(10L)).willReturn(Optional.empty());

            TrophyRankingResponse response = rankingService.getTrophyRanking(10L, 0, 10);

            assertThat(response.rankings()).isEmpty();
            assertThat(response.myRank()).isNull();
        }
    }

    @Nested
    @DisplayName("GetContinentRanking")
    class GetContinentRanking {

        private UserTrophy trophyWithScore(int score, UserTrophy.League league) {
            UserTrophy trophy = UserTrophy.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(trophy, "score", score);
            ReflectionTestUtils.setField(trophy, "league", league);
            return trophy;
        }

        private Continent continent(long id, int minTrophy) {
            Continent continent =
                    Continent.builder()
                            .name("글리치")
                            .themeColor("#8b50ff")
                            .minTrophyRequired(minTrophy)
                            .build();
            ReflectionTestUtils.setField(continent, "id", id);
            return continent;
        }

        @Test
        @DisplayName("대륙 트로피 밴드 내 유저 → 트로피 점수 순위 + 내 순위 반환")
        void success_withBand() {
            given(continentRepository.findById(5L)).willReturn(Optional.of(continent(5L, 1000)));
            given(continentRepository.findNextMinTrophyAbove(1000)).willReturn(2000);
            UserTrophy trophy = trophyWithScore(1500, UserTrophy.League.GOLD);
            given(userTrophyRepository.findInScoreBandOrderByScoreDesc(eq(1000), eq(2000), any()))
                    .willReturn(List.of(trophy));
            given(userTrophyRepository.findById(10L)).willReturn(Optional.of(trophy));
            given(userTrophyRepository.countByScoreGreaterThanAndScoreLessThan(1500, 2000))
                    .willReturn(0L);
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));

            ContinentRankingResponse response = rankingService.getContinentRanking(10L, 5L, 0, 10);

            assertThat(response.continentId()).isEqualTo(5L);
            assertThat(response.type()).isEqualTo("CONTINENT_TROPHY");
            assertThat(response.rankings()).hasSize(1);
            assertThat(response.rankings().get(0).rank()).isEqualTo(1);
            assertThat(response.rankings().get(0).userId()).isEqualTo(10L);
            assertThat(response.rankings().get(0).nickname()).isEqualTo("테스터");
            assertThat(response.rankings().get(0).score()).isEqualTo(1500);
            assertThat(response.myRank()).isEqualTo(1);
            assertThat(response.myScore()).isEqualTo(1500L);
        }

        @Test
        @DisplayName("최상위 대륙(다음 등급 없음) → 상한 무제한으로 조회")
        void success_topTier() {
            given(continentRepository.findById(8L)).willReturn(Optional.of(continent(8L, 5000)));
            given(continentRepository.findNextMinTrophyAbove(5000)).willReturn(null);
            given(
                            userTrophyRepository.findInScoreBandOrderByScoreDesc(
                                    eq(5000), eq(Integer.MAX_VALUE), any()))
                    .willReturn(List.of());
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));

            ContinentRankingResponse response = rankingService.getContinentRanking(null, 8L, 0, 10);

            assertThat(response.rankings()).isEmpty();
            assertThat(response.myRank()).isNull();
        }

        @Test
        @DisplayName("내 트로피가 대륙 밴드 밖 → myRank null, 랭킹은 반환")
        void myTrophyOutOfBand() {
            given(continentRepository.findById(5L)).willReturn(Optional.of(continent(5L, 1000)));
            given(continentRepository.findNextMinTrophyAbove(1000)).willReturn(2000);
            given(userTrophyRepository.findInScoreBandOrderByScoreDesc(eq(1000), eq(2000), any()))
                    .willReturn(List.of());
            given(userTrophyRepository.findById(10L))
                    .willReturn(Optional.of(trophyWithScore(500, UserTrophy.League.BRONZE)));
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));

            ContinentRankingResponse response = rankingService.getContinentRanking(10L, 5L, 0, 10);

            assertThat(response.myRank()).isNull();
            assertThat(response.myScore()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 대륙 → CONTINENT_NOT_FOUND 예외")
        void continentNotFound() {
            given(continentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> rankingService.getContinentRanking(10L, 999L, 0, 10))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTINENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GetMyRanking")
    class GetMyRanking {

        @Test
        @DisplayName("활성 시즌 존재 → 두 카테고리 순위 모두 반환")
        void success() {
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRank(eq("ranking:season:1:territory_hold"), eq("10")))
                    .willReturn(2L);
            given(zSetOperations.score(eq("ranking:season:1:territory_hold"), eq("10")))
                    .willReturn(4000.0);
            given(zSetOperations.reverseRank(eq("ranking:season:1:auction_spend"), eq("10")))
                    .willReturn(4L);
            given(zSetOperations.score(eq("ranking:season:1:auction_spend"), eq("10")))
                    .willReturn(1500.0);

            MyRankingResponse response = rankingService.getMyRanking(10L);

            assertThat(response.seasonId()).isEqualTo(1L);
            assertThat(response.territoryHold().rank()).isEqualTo(3);
            assertThat(response.territoryHold().score()).isEqualTo(4000L);
            assertThat(response.auctionSpend().rank()).isEqualTo(5);
            assertThat(response.auctionSpend().totalSpentAP()).isEqualTo(1500L);
        }
    }

    @Nested
    @DisplayName("HandleAuctionSettled")
    class HandleAuctionSettled {

        @Test
        @DisplayName("이벤트 수신 → ZINCRBY 호출 확인")
        void success() {
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            AuctionSettledEvent event = new AuctionSettledEvent(10L, 1L, 2000);

            rankingService.handleAuctionSettled(event);

            then(zSetOperations)
                    .should()
                    .incrementScore("ranking:season:1:auction_spend", "10", 2000.0);
        }
    }

    @Nested
    @DisplayName("HandleTerritoryHoldStarted")
    class HandleTerritoryHoldStarted {

        // handleTerritoryHoldStarted는 @Transactional(propagation = REQUIRES_NEW)로 선언되어
        // 이벤트 발행 트랜잭션과 독립된 새 트랜잭션에서 DB 쓰기를 수행한다.
        // 단위 테스트에서는 Spring 컨텍스트 없이 실행되므로 propagation 자체는 검증할 수 없고,
        // save() 호출 여부로 DB 쓰기 동작을 검증한다.
        @Test
        @DisplayName("이벤트 수신 → SeasonTerritoryHold 저장 확인")
        void success() {
            TerritoryHoldStartedEvent event =
                    new TerritoryHoldStartedEvent(
                            10L, 1L, 100L, "S", LocalDateTime.of(2026, 5, 1, 0, 0));

            given(seasonRepository.findById(1L)).willReturn(Optional.of(season));
            given(userRepository.findById(10L)).willReturn(Optional.of(user));
            given(territoryRepository.findById(100L)).willReturn(Optional.of(territory));

            rankingService.handleTerritoryHoldStarted(event);

            then(seasonTerritoryHoldRepository).should().save(any(SeasonTerritoryHold.class));
        }
    }

    @Nested
    @DisplayName("HandleTerritoryHoldClosed")
    class HandleTerritoryHoldClosed {

        // handleTerritoryHoldClosed도 @Transactional(propagation = REQUIRES_NEW)로 선언되어
        // 독립 트랜잭션에서 hold.closeHold()를 통해 heldUntil을 설정한다.
        @Test
        @DisplayName("열린 레코드 존재 → closeHold() 호출 확인")
        void success() {
            LocalDateTime closedAt = LocalDateTime.of(2026, 5, 10, 0, 0);
            TerritoryHoldClosedEvent event = new TerritoryHoldClosedEvent(10L, 1L, 100L, closedAt);

            given(
                            seasonTerritoryHoldRepository
                                    .findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(
                                            1L, 10L, 100L))
                    .willReturn(Optional.of(hold));

            rankingService.handleTerritoryHoldClosed(event);

            assertThat(hold.getHeldUntil()).isEqualTo(closedAt);
        }

        @Test
        @DisplayName("열린 레코드 없을 때 → 예외 없이 정상 종료 (ifPresent로 처리)")
        void notFound() {
            TerritoryHoldClosedEvent event =
                    new TerritoryHoldClosedEvent(10L, 1L, 100L, LocalDateTime.now());

            given(
                            seasonTerritoryHoldRepository
                                    .findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(
                                            1L, 10L, 100L))
                    .willReturn(Optional.empty());

            // ifPresent 사용이므로 예외 없이 종료 — save 호출 안 됨
            rankingService.handleTerritoryHoldClosed(event);

            then(seasonTerritoryHoldRepository)
                    .should()
                    .findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(1L, 10L, 100L);
        }
    }

    @Nested
    @DisplayName("AggregateTerritoryHoldRanking")
    class AggregateTerritoryHoldRanking {

        @Test
        @DisplayName("hold 목록 존재 → ZADD 호출 및 스코어 계산 확인")
        void success() {
            LocalDateTime heldFrom = LocalDateTime.of(2026, 5, 1, 0, 0);
            LocalDateTime heldUntil =
                    LocalDateTime.of(2026, 5, 1, 1, 0); // 3600초, grade S(5) = 18000
            SeasonTerritoryHold closedHold =
                    SeasonTerritoryHold.builder()
                            .season(season)
                            .user(user)
                            .territory(territory)
                            .grade("S")
                            .heldFrom(heldFrom)
                            .build();
            ReflectionTestUtils.setField(closedHold, "heldUntil", heldUntil);

            given(seasonTerritoryHoldRepository.findAllBySeasonId(1L))
                    .willReturn(List.of(closedHold));
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

            rankingService.aggregateTerritoryHoldRanking(1L);

            // delete 후 ZADD 호출
            then(stringRedisTemplate).should().delete("ranking:season:1:territory_hold");
            then(zSetOperations)
                    .should()
                    .add(eq("ranking:season:1:territory_hold"), eq("10"), anyDouble());
            then(valueOperations)
                    .should()
                    .set(eq("ranking:season:1:territory_hold:updated_at"), anyString());
        }
    }

    // ── ZSetOperations.TypedTuple 테스트용 구현체 ──────────────────────────────

    private static class TestTypedTuple implements ZSetOperations.TypedTuple<String> {
        private final String value;
        private final Double score;

        TestTypedTuple(String value, Double score) {
            this.value = value;
            this.score = score;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public Double getScore() {
            return score;
        }

        @Override
        public int compareTo(ZSetOperations.TypedTuple<String> o) {
            return Double.compare(score, o.getScore());
        }
    }
}
