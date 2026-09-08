package com.territorial.season.domain.season.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.territorial.season.client.CombatResourceClient;
import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.UserTrophy;
import com.territorial.season.domain.season.entity.UserTrophy.League;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.SeasonRewardRepository;
import com.territorial.season.domain.season.repository.UserSeasonPassRepository;
import com.territorial.season.domain.season.repository.UserTrophyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeasonEndBatchServiceTest {

    @Mock private SeasonRepository seasonRepository;
    @Mock private UserTrophyRepository userTrophyRepository;
    @Mock private SeasonRewardRepository seasonRewardRepository;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private UserSeasonPassRepository userSeasonPassRepository;

    @InjectMocks private SeasonEndBatchService service;

    @DisplayName("미처리 종료 시즌이 없으면 아무 정산도 하지 않는다")
    @Test
    void noEndedSeason_doesNothing() {
        when(seasonRepository.findFirstUnprocessedEndedSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        service.runIfSeasonEnded();

        verifyNoInteractions(
                userTrophyRepository,
                seasonRewardRepository,
                combatResourceClient,
                userSeasonPassRepository);
    }

    @DisplayName("정산 — GOLD 유저에게 규정 보상(GP 600·공격권 1/1) 지급 + 트로피 리셋 + 패스 종료 + 시즌 처리완료")
    @Test
    void settlesGoldUser_issuesRewardsResetsAndMarksProcessed() {
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(1L);
        UserTrophy trophy = mock(UserTrophy.class);
        when(trophy.getUserId()).thenReturn(10L);
        when(trophy.getLeague()).thenReturn(League.GOLD);

        when(seasonRepository.findFirstUnprocessedEndedSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.of(season));
        when(userTrophyRepository.findAllBySeasonId(1L)).thenReturn(List.of(trophy));
        when(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).thenReturn(false);

        service.runIfSeasonEnded();

        verify(seasonRewardRepository).save(any());
        verify(combatResourceClient).creditGp(eq(10L), eq(600), anyString());
        verify(combatResourceClient).creditAttackTokens(eq(10L), eq(1), eq(1), anyString());
        verify(trophy).applySeasonReset(1L);
        verify(userSeasonPassRepository).deactivateAllActive();
        verify(season).markProcessed();
    }

    @DisplayName("정산 멱등성 — 이미 보상 지급된 유저는 보상을 건너뛰되 트로피 리셋은 수행")
    @Test
    void alreadyRewarded_skipsRewardButStillResets() {
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(1L);
        UserTrophy trophy = mock(UserTrophy.class);
        when(trophy.getUserId()).thenReturn(10L);

        when(seasonRepository.findFirstUnprocessedEndedSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.of(season));
        when(userTrophyRepository.findAllBySeasonId(1L)).thenReturn(List.of(trophy));
        when(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).thenReturn(true);

        service.runIfSeasonEnded();

        verify(seasonRewardRepository, never()).save(any());
        verify(combatResourceClient, never()).creditGp(any(), anyInt(), anyString());
        verify(trophy).applySeasonReset(1L);
        verify(season).markProcessed();
    }

    @DisplayName("정산 — BRONZE는 GP만 지급하고 공격권은 지급하지 않는다(0/0)")
    @Test
    void bronzeUser_getsGpbutNoTokens() {
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(1L);
        UserTrophy trophy = mock(UserTrophy.class);
        when(trophy.getUserId()).thenReturn(10L);
        when(trophy.getLeague()).thenReturn(League.BRONZE);

        when(seasonRepository.findFirstUnprocessedEndedSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.of(season));
        when(userTrophyRepository.findAllBySeasonId(1L)).thenReturn(List.of(trophy));
        when(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).thenReturn(false);

        service.runIfSeasonEnded();

        verify(combatResourceClient).creditGp(eq(10L), eq(100), anyString());
        verify(combatResourceClient, never())
                .creditAttackTokens(any(), anyInt(), anyInt(), anyString());
    }
}
