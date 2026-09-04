package com.territorial.ranking.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.ranking.client.ContinentBandClient;
import com.territorial.ranking.client.ContinentBandClient.TrophyBand;
import com.territorial.ranking.client.NicknameClient;
import com.territorial.ranking.client.SeasonGameEventClient;
import com.territorial.ranking.client.SeasonQueryClient;
import com.territorial.ranking.client.SeasonQueryClient.ActiveSeason;
import com.territorial.ranking.client.SeasonTrophyClient;
import com.territorial.ranking.client.SeasonTrophyClient.Trophy;
import com.territorial.ranking.domain.ranking.dto.AuctionSpendRankingResponse;
import com.territorial.ranking.domain.ranking.dto.ContinentRankingResponse;
import com.territorial.ranking.domain.ranking.dto.MyRankingResponse;
import com.territorial.ranking.domain.ranking.dto.TerritoryHoldRankingResponse;
import com.territorial.ranking.domain.ranking.dto.TrophyRankingResponse;
import com.territorial.ranking.domain.ranking.entity.SeasonTerritoryHold;
import com.territorial.ranking.domain.ranking.repository.SeasonTerritoryHoldRepository;
import com.territorial.ranking.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @InjectMocks private RankingService rankingService;

    @Mock private SeasonTerritoryHoldRepository seasonTerritoryHoldRepository;
    @Mock private SeasonQueryClient seasonQueryClient;
    @Mock private SeasonTrophyClient seasonTrophyClient;
    @Mock private SeasonGameEventClient seasonGameEventClient;
    @Mock private ContinentBandClient continentBandClient;
    @Mock private NicknameClient nicknameClient;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private ValueOperations<String, String> valueOperations;

    private ActiveSeason activeSeason;

    @BeforeEach
    void setUp() {
        activeSeason =
                new ActiveSeason(
                        1L,
                        1,
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        LocalDateTime.of(2026, 12, 31, 23, 59));
    }

    private SeasonTerritoryHold hold(Long userId, String grade, LocalDateTime from) {
        return SeasonTerritoryHold.builder()
                .seasonId(1L)
                .userId(userId)
                .territoryId(100L)
                .grade(grade)
                .heldFrom(from)
                .build();
    }

    @Nested
    @DisplayName("GetTerritoryHoldRanking")
    class GetTerritoryHoldRanking {

        @Test
        @DisplayName("시즌 존재 + Redis 데이터 → rankings 반환")
        void success() {
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.of(activeSeason));
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);

            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            tuples.add(new TestTypedTuple("10", 5000.0));
            given(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(9L)))
                    .willReturn(tuples);
            given(zSetOperations.reverseRank(anyString(), eq("10"))).willReturn(0L);
            given(zSetOperations.score(anyString(), eq("10"))).willReturn(5000.0);
            given(nicknameClient.getNicknames(anyList())).willReturn(Map.of(10L, "테스터"));
            given(seasonTerritoryHoldRepository.findAllBySeasonId(1L))
                    .willReturn(List.of(hold(10L, "S", LocalDateTime.of(2026, 5, 1, 0, 0))));

            TerritoryHoldRankingResponse response =
                    rankingService.getTerritoryHoldRanking(10L, 0, 10);

            assertThat(response.seasonId()).isEqualTo(1L);
            assertThat(response.rankings()).hasSize(1);
            assertThat(response.rankings().get(0).userId()).isEqualTo(10L);
            assertThat(response.rankings().get(0).nickname()).isEqualTo("테스터");
            assertThat(response.myRank()).isEqualTo(1);
        }

        @Test
        @DisplayName("활성 시즌 없음 → 빈 응답")
        void noSeason() {
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.empty());

            TerritoryHoldRankingResponse response =
                    rankingService.getTerritoryHoldRanking(null, 0, 10);

            assertThat(response.seasonId()).isNull();
            assertThat(response.rankings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GetAuctionSpendRanking")
    class GetAuctionSpendRanking {

        @Test
        @DisplayName("시즌 존재 + Redis 데이터 → rankings 반환")
        void success() {
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.of(activeSeason));
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            tuples.add(new TestTypedTuple("10", 3000.0));
            given(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(9L)))
                    .willReturn(tuples);
            given(zSetOperations.reverseRank(anyString(), eq("10"))).willReturn(0L);
            given(zSetOperations.score(anyString(), eq("10"))).willReturn(3000.0);
            given(nicknameClient.getNicknames(anyList())).willReturn(Map.of(10L, "테스터"));

            AuctionSpendRankingResponse response =
                    rankingService.getAuctionSpendRanking(10L, 0, 10);

            assertThat(response.rankings().get(0).totalSpentAP()).isEqualTo(3000L);
            assertThat(response.myScore()).isEqualTo(3000L);
        }
    }

    @Nested
    @DisplayName("GetTrophyRanking")
    class GetTrophyRanking {

        @Test
        @DisplayName("트로피 보유 → 랭킹 + 내 순위")
        void success() {
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.of(activeSeason));
            given(seasonTrophyClient.getRanking(0, 10))
                    .willReturn(List.of(new Trophy(10L, 1200, "GOLD")));
            given(nicknameClient.getNicknames(anyList())).willReturn(Map.of(10L, "테스터"));
            given(seasonTrophyClient.getTrophy(10L))
                    .willReturn(Optional.of(new Trophy(10L, 1200, "GOLD")));
            given(seasonTrophyClient.countAbove(1200)).willReturn(0L);

            TrophyRankingResponse response = rankingService.getTrophyRanking(10L, 0, 10);

            assertThat(response.rankings().get(0).league()).isEqualTo("GOLD");
            assertThat(response.myRank()).isEqualTo(1);
            assertThat(response.myLeague()).isEqualTo("GOLD");
        }

        @Test
        @DisplayName("내 트로피 없음 → myRank null")
        void noMyTrophy() {
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.of(activeSeason));
            given(seasonTrophyClient.getRanking(0, 10)).willReturn(List.of());
            given(seasonTrophyClient.getTrophy(10L)).willReturn(Optional.empty());
            given(nicknameClient.getNicknames(anyList())).willReturn(Map.of());

            TrophyRankingResponse response = rankingService.getTrophyRanking(10L, 0, 10);

            assertThat(response.rankings()).isEmpty();
            assertThat(response.myRank()).isNull();
        }
    }

    @Nested
    @DisplayName("GetContinentRanking")
    class GetContinentRanking {

        @Test
        @DisplayName("대륙 밴드 내 유저 → 점수 순위 + 내 순위")
        void success() {
            given(continentBandClient.getTrophyBand(5L)).willReturn(new TrophyBand(1000, 2000));
            given(seasonTrophyClient.getBand(1000, 2000, 0, 10))
                    .willReturn(List.of(new Trophy(10L, 1500, "GOLD")));
            given(nicknameClient.getNicknames(anyList())).willReturn(Map.of(10L, "테스터"));
            given(seasonTrophyClient.getTrophy(10L))
                    .willReturn(Optional.of(new Trophy(10L, 1500, "GOLD")));
            given(seasonTrophyClient.countBand(1500, 2000)).willReturn(0L);
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.of(activeSeason));

            ContinentRankingResponse response = rankingService.getContinentRanking(10L, 5L, 0, 10);

            assertThat(response.rankings().get(0).nickname()).isEqualTo("테스터");
            assertThat(response.rankings().get(0).score()).isEqualTo(1500);
            assertThat(response.myRank()).isEqualTo(1);
        }

        @Test
        @DisplayName("내 트로피가 밴드 밖 → myRank null")
        void outOfBand() {
            given(continentBandClient.getTrophyBand(5L)).willReturn(new TrophyBand(1000, 2000));
            given(seasonTrophyClient.getBand(1000, 2000, 0, 10)).willReturn(List.of());
            given(nicknameClient.getNicknames(anyList())).willReturn(Map.of());
            given(seasonTrophyClient.getTrophy(10L))
                    .willReturn(Optional.of(new Trophy(10L, 500, "BRONZE")));
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.of(activeSeason));

            ContinentRankingResponse response = rankingService.getContinentRanking(10L, 5L, 0, 10);

            assertThat(response.myRank()).isNull();
            assertThat(response.myScore()).isNull();
        }

        @Test
        @DisplayName("없는 대륙 → map 계약이 CONTINENT_NOT_FOUND 전파")
        void notFound() {
            given(continentBandClient.getTrophyBand(999L))
                    .willThrow(new CustomException(ErrorCode.CONTINENT_NOT_FOUND));

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
        @DisplayName("활성 시즌 존재 → 두 카테고리 순위 반환")
        void success() {
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.of(activeSeason));
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

            assertThat(response.territoryHold().rank()).isEqualTo(3);
            assertThat(response.auctionSpend().totalSpentAP()).isEqualTo(1500L);
        }
    }

    @Nested
    @DisplayName("onAuctionSettled")
    class OnAuctionSettled {

        @Test
        @DisplayName("활성 시즌 → 게임이벤트 위임 + 경매소비 증분 + 점유시작 저장")
        void withActiveSeason() {
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.of(activeSeason));
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);

            rankingService.onAuctionSettled(10L, 100L, "S", 2000);

            then(seasonGameEventClient).should().sendGameEvent(10L, "AUCTION_WIN");
            then(zSetOperations)
                    .should()
                    .incrementScore("ranking:season:1:auction_spend", "10", 2000.0);
            then(seasonTerritoryHoldRepository).should().save(any(SeasonTerritoryHold.class));
        }

        @Test
        @DisplayName("시즌 외 → 게임이벤트만 위임, 랭킹 기록 없음")
        void noActiveSeason() {
            given(seasonQueryClient.getActiveSeason()).willReturn(Optional.empty());

            rankingService.onAuctionSettled(10L, 100L, "S", 2000);

            then(seasonGameEventClient).should().sendGameEvent(10L, "AUCTION_WIN");
            then(seasonTerritoryHoldRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("closeTerritoryHold")
    class CloseTerritoryHold {

        @Test
        @DisplayName("열린 기록 존재 → closeHold 호출")
        void success() {
            LocalDateTime closedAt = LocalDateTime.of(2026, 5, 10, 0, 0);
            SeasonTerritoryHold openHold = hold(10L, "S", LocalDateTime.of(2026, 5, 1, 0, 0));
            given(
                            seasonTerritoryHoldRepository
                                    .findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(
                                            1L, 10L, 100L))
                    .willReturn(Optional.of(openHold));

            rankingService.closeTerritoryHold(10L, 1L, 100L, closedAt);

            assertThat(openHold.getHeldUntil()).isEqualTo(closedAt);
        }

        @Test
        @DisplayName("열린 기록 없음 → 예외 없이 종료")
        void notFound() {
            given(
                            seasonTerritoryHoldRepository
                                    .findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(
                                            1L, 10L, 100L))
                    .willReturn(Optional.empty());

            rankingService.closeTerritoryHold(10L, 1L, 100L, LocalDateTime.now());

            then(seasonTerritoryHoldRepository)
                    .should()
                    .findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(1L, 10L, 100L);
        }
    }

    @Nested
    @DisplayName("aggregateTerritoryHoldRanking")
    class AggregateTerritoryHoldRanking {

        @Test
        @DisplayName("hold 목록 → ZADD 집계")
        void success() {
            LocalDateTime from = LocalDateTime.of(2026, 5, 1, 0, 0);
            SeasonTerritoryHold closed = hold(10L, "S", from);
            ReflectionTestUtils.setField(closed, "heldUntil", from.plusHours(1));
            given(seasonTerritoryHoldRepository.findAllBySeasonId(1L)).willReturn(List.of(closed));
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

            rankingService.aggregateTerritoryHoldRanking(1L);

            then(stringRedisTemplate).should().delete("ranking:season:1:territory_hold");
            then(zSetOperations)
                    .should()
                    .add(eq("ranking:season:1:territory_hold"), eq("10"), anyDouble());
        }
    }

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
