package com.territorial.ranking.domain.ranking.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.territorial.ranking.client.ContinentBandClient;
import com.territorial.ranking.client.NicknameClient;
import com.territorial.ranking.client.SeasonGameEventClient;
import com.territorial.ranking.client.SeasonQueryClient;
import com.territorial.ranking.client.SeasonTrophyClient;
import com.territorial.ranking.domain.ranking.entity.SeasonTerritoryHold;
import com.territorial.ranking.domain.ranking.repository.SeasonTerritoryHoldRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class RankingHoldAggregationTest {

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

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 3, 1, 0, 0);

    private SeasonTerritoryHold closedHold(Long userId, String grade, long seconds) {
        SeasonTerritoryHold hold =
                SeasonTerritoryHold.builder()
                        .seasonId(1L)
                        .userId(userId)
                        .territoryId(100L)
                        .grade(grade)
                        .heldFrom(FROM)
                        .build();
        hold.closeHold(FROM.plusSeconds(seconds));
        return hold;
    }

    @DisplayName("영토 보유 랭킹 집계 — (보유 초 × 등급 가중치)를 유저별로 합산해 ZSet 점수로 기록")
    @Test
    void aggregate_weightsBySecondsAndGradeThenSumsPerUser() {
        // user 1: A(4)×100s=400 + B(3)×100s=300 = 700
        // user 2: S(5)×200s = 1000
        when(seasonTerritoryHoldRepository.findAllBySeasonId(1L))
                .thenReturn(
                        List.of(
                                closedHold(1L, "A", 100),
                                closedHold(1L, "B", 100),
                                closedHold(2L, "S", 200)));
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        rankingService.aggregateTerritoryHoldRanking(1L);

        verify(zSetOperations).add(anyString(), eq("1"), eq(700.0));
        verify(zSetOperations).add(anyString(), eq("2"), eq(1000.0));
    }
}
