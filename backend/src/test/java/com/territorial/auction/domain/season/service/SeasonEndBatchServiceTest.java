package com.territorial.auction.domain.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.combat.client.CombatResourceClient;
import com.territorial.auction.domain.season.TierPolicy;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.entity.SeasonReward;
import com.territorial.auction.domain.season.entity.UserTrophy;
import com.territorial.auction.domain.season.entity.UserTrophy.League;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.season.repository.SeasonRewardRepository;
import com.territorial.auction.domain.season.repository.UserSeasonPassRepository;
import com.territorial.auction.domain.season.repository.UserTrophyRepository;
import com.territorial.auction.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonEndBatchServiceTest {

    @InjectMocks private SeasonEndBatchService seasonEndBatchService;

    @Mock private SeasonRepository seasonRepository;
    @Mock private UserTrophyRepository userTrophyRepository;
    @Mock private SeasonRewardRepository seasonRewardRepository;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private UserSeasonPassRepository userSeasonPassRepository;

    private Season season;
    private User user;
    private UserTrophy trophy;

    @BeforeEach
    void setUp() {
        season =
                Season.builder()
                        .seasonNumber(1)
                        .startedAt(LocalDateTime.now().minusDays(30))
                        .endedAt(LocalDateTime.now().minusMinutes(1))
                        .build();
        ReflectionTestUtils.setField(season, "id", 1L);

        user =
                User.builder()
                        .username("tester")
                        .email("t@t.com")
                        .passwordHash("hash")
                        .nickname("tester")
                        .build();
        ReflectionTestUtils.setField(user, "id", 10L);

        trophy = UserTrophy.builder().user(user).season(season).build();
        ReflectionTestUtils.setField(trophy, "userId", 10L);
        ReflectionTestUtils.setField(trophy, "score", 1500); // Gold 3
        ReflectionTestUtils.setField(trophy, "league", League.GOLD);
    }

    @Nested
    @DisplayName("runIfSeasonEnded")
    class RunIfSeasonEnded {

        @Test
        @DisplayName("처리할 종료 시즌 없음 → no-op")
        void runIfSeasonEnded_noEndedSeason() {
            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.empty());

            seasonEndBatchService.runIfSeasonEnded();

            then(userTrophyRepository).should(never()).findAllBySeasonId(any());
        }

        @Test
        @DisplayName("종료 시즌 존재 → 보상 지급 + 트로피 리셋 + processedAt 설정")
        void runIfSeasonEnded_processesEndedSeason() {
            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllBySeasonId(1L)).willReturn(List.of(trophy));
            given(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).willReturn(false);
            seasonEndBatchService.runIfSeasonEnded();

            then(seasonRewardRepository).should().save(any(SeasonReward.class));
            assertThat(season.getProcessedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("보상 지급")
    class IssueRewards {

        @Test
        @DisplayName("이미 지급된 유저 → 중복 저장 안 함")
        void issueRewards_skipAlreadyIssued() {
            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllBySeasonId(1L)).willReturn(List.of(trophy));
            given(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).willReturn(true);

            seasonEndBatchService.runIfSeasonEnded();

            then(seasonRewardRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Gold 3 → GP 600 즉시 지급")
        void issueRewards_goldLeagueGp() {
            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllBySeasonId(1L)).willReturn(List.of(trophy));
            given(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).willReturn(false);
            seasonEndBatchService.runIfSeasonEnded();

            then(combatResourceClient).should().creditGp(10L, 600, "SEASON_END:1:10:GP");
        }

        @Test
        @DisplayName("Gold 3 → 일반 공격권 1개·정밀 공격권 1개 즉시 적립")
        void issueRewards_goldLeagueAttackTokens() {
            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllBySeasonId(1L)).willReturn(List.of(trophy));
            given(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).willReturn(false);
            seasonEndBatchService.runIfSeasonEnded();

            then(combatResourceClient)
                    .should()
                    .creditAttackTokens(10L, 1, 1, "SEASON_END:1:10:ATTACK_TOKEN");
        }

        @Test
        @DisplayName("Bronze 3 → 공격권 지급 없음 (0 토큰)")
        void issueRewards_bronzeNoAttackTokens() {
            ReflectionTestUtils.setField(trophy, "score", 0);
            ReflectionTestUtils.setField(trophy, "league", League.BRONZE);

            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllBySeasonId(1L)).willReturn(List.of(trophy));
            given(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).willReturn(false);
            seasonEndBatchService.runIfSeasonEnded();

            then(combatResourceClient)
                    .should(never())
                    .creditAttackTokens(any(), anyInt(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("트로피 리셋")
    class ResetTrophies {

        @Test
        @DisplayName("Gold 3(1500) → Silver 1(1167)로 서브티어 한 단계 강등")
        void resetTrophies_goldThreeDemotedToSilverOne() {
            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllBySeasonId(1L)).willReturn(List.of(trophy));
            given(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).willReturn(false);
            seasonEndBatchService.runIfSeasonEnded();

            assertThat(trophy.getScore()).isEqualTo(1167);
            assertThat(trophy.getLeague()).isEqualTo(League.SILVER);
        }

        @Test
        @DisplayName("Bronze 3(0) → 0 유지 (최하위 티어)")
        void resetTrophies_bronzeThreeStaysAtZero() {
            ReflectionTestUtils.setField(trophy, "score", 0);
            ReflectionTestUtils.setField(trophy, "league", League.BRONZE);

            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllBySeasonId(1L)).willReturn(List.of(trophy));
            given(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).willReturn(false);
            seasonEndBatchService.runIfSeasonEnded();

            assertThat(trophy.getScore()).isEqualTo(0);
        }

        @Test
        @DisplayName("Champion(8000) → Diamond 1(6667)로 강등")
        void resetTrophies_championDemotedToDiamondOne() {
            ReflectionTestUtils.setField(trophy, "score", 8000);
            ReflectionTestUtils.setField(trophy, "league", League.CHAMPION);

            given(seasonRepository.findFirstUnprocessedEndedSeason(any()))
                    .willReturn(Optional.of(season));
            given(userTrophyRepository.findAllBySeasonId(1L)).willReturn(List.of(trophy));
            given(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).willReturn(false);
            seasonEndBatchService.runIfSeasonEnded();

            assertThat(trophy.getScore()).isEqualTo(6667);
            assertThat(trophy.getLeague()).isEqualTo(League.DIAMOND);
        }

        @Test
        @DisplayName("같은 시즌 ID로 재호출 → 트로피 점수 불변 (멱등성)")
        void resetTrophies_idempotentWithSameSeasonId() {
            trophy.applySeasonReset(1L);
            int scoreAfterFirstReset = trophy.getScore();
            trophy.applySeasonReset(1L);

            assertThat(trophy.getScore()).isEqualTo(scoreAfterFirstReset);
        }
    }

    @Nested
    @DisplayName("TierPolicy 계산 검증")
    class TierPolicyTest {

        @Test
        @DisplayName("Diamond 1(6667) → Diamond 2 min(5334) 리셋")
        void diamondOneDemotedToDiamondTwo() {
            assertThat(TierPolicy.calculateResetScore(6667)).isEqualTo(5334);
        }

        @Test
        @DisplayName("Silver 3(500) → Bronze 1 min(334) 리셋")
        void silverThreeDemotedToBronzeOne() {
            assertThat(TierPolicy.calculateResetScore(500)).isEqualTo(334);
        }

        @Test
        @DisplayName("점수로 League 계산")
        void calculateLeague() {
            assertThat(TierPolicy.calculateLeague(499)).isEqualTo(League.BRONZE);
            assertThat(TierPolicy.calculateLeague(1500)).isEqualTo(League.GOLD);
            assertThat(TierPolicy.calculateLeague(8000)).isEqualTo(League.CHAMPION);
        }
    }
}
