package com.territorial.season.domain.season.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.territorial.season.client.CombatResourceClient;
import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.UserTrophy;
import com.territorial.season.domain.season.entity.UserTrophy.League;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.SeasonRewardRepository;
import com.territorial.season.domain.season.repository.UserSeasonPassRepository;
import com.territorial.season.domain.season.repository.UserTrophyRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeasonRewardIssueServiceTest {

    @Mock private SeasonRepository seasonRepository;
    @Mock private UserTrophyRepository userTrophyRepository;
    @Mock private SeasonRewardRepository seasonRewardRepository;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private UserSeasonPassRepository userSeasonPassRepository;

    @InjectMocks private SeasonRewardIssueService service;

    @DisplayName("지급 — GOLD 유저: GP 600·공격권 1/1 지급 + reward 저장 + 트로피 리셋")
    @Test
    void issueUserReward_gold() {
        Season season = mock(Season.class);
        UserTrophy trophy = mock(UserTrophy.class);
        when(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).thenReturn(false);
        when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
        when(userTrophyRepository.findById(10L)).thenReturn(Optional.of(trophy));

        service.issueUserReward(1L, 10L, League.GOLD);

        verify(combatResourceClient).creditGp(eq(10L), eq(600), anyString());
        verify(combatResourceClient).creditAttackTokens(eq(10L), eq(1), eq(1), anyString());
        verify(seasonRewardRepository).save(any());
        verify(trophy).applySeasonReset(1L);
    }

    @DisplayName("지급 — BRONZE는 GP만, 공격권 없음(0/0)")
    @Test
    void issueUserReward_bronze_noTokens() {
        Season season = mock(Season.class);
        UserTrophy trophy = mock(UserTrophy.class);
        when(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).thenReturn(false);
        when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
        when(userTrophyRepository.findById(10L)).thenReturn(Optional.of(trophy));

        service.issueUserReward(1L, 10L, League.BRONZE);

        verify(combatResourceClient).creditGp(eq(10L), eq(100), anyString());
        verify(combatResourceClient, never())
                .creditAttackTokens(any(), anyInt(), anyInt(), anyString());
    }

    @DisplayName("멱등 — 이미 지급된 유저(reward 레코드 존재)는 지급·저장·리셋 모두 건너뜀")
    @Test
    void issueUserReward_alreadyRewarded_skips() {
        when(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).thenReturn(true);

        service.issueUserReward(1L, 10L, League.GOLD);

        verify(combatResourceClient, never()).creditGp(any(), anyInt(), anyString());
        verify(seasonRewardRepository, never()).save(any());
    }

    @DisplayName("credit-first — 지급(HTTP) 실패 시 로컬 reward 저장 안 함(롤백)")
    @Test
    void issueUserReward_creditFails_noLocalWrite() {
        when(seasonRewardRepository.existsBySeasonIdAndUserId(1L, 10L)).thenReturn(false);
        doThrow(new RuntimeException("combat 다운"))
                .when(combatResourceClient)
                .creditGp(eq(10L), anyInt(), anyString());

        assertThatThrownBy(() -> service.issueUserReward(1L, 10L, League.GOLD))
                .isInstanceOf(RuntimeException.class);

        verify(seasonRewardRepository, never()).save(any());
    }

    @DisplayName("종료 확정 — 활성 패스 일괄 비활성 + 시즌 processed 마킹")
    @Test
    void finalizeSeason() {
        Season season = mock(Season.class);
        when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));

        service.finalizeSeason(1L);

        verify(userSeasonPassRepository).deactivateAllActive();
        verify(season).markProcessed();
    }
}
