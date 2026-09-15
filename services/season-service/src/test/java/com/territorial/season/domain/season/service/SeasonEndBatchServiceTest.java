package com.territorial.season.domain.season.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.UserTrophy;
import com.territorial.season.domain.season.entity.UserTrophy.League;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.domain.season.repository.SeasonRewardRepository;
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
    @Mock private SeasonRewardIssueService rewardIssueService;

    @InjectMocks private SeasonEndBatchService service;

    @DisplayName("미처리 종료 시즌이 없으면 아무 작업도 하지 않는다")
    @Test
    void noEndedSeason_doesNothing() {
        when(seasonRepository.findFirstUnprocessedEndedSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        service.runIfSeasonEnded();

        verifyNoInteractions(userTrophyRepository, seasonRewardRepository, rewardIssueService);
    }

    @DisplayName("오케스트레이션 — 유저마다 지급 위임하고, 전원 지급 확인되면 시즌 종료 확정")
    @Test
    void allIssued_delegatesPerUserAndFinalizes() {
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(1L);
        UserTrophy gold = trophy(10L, League.GOLD);
        UserTrophy bronze = trophy(11L, League.BRONZE);
        when(seasonRepository.findFirstUnprocessedEndedSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.of(season));
        when(userTrophyRepository.findAllBySeasonId(1L)).thenReturn(List.of(gold, bronze));
        when(seasonRewardRepository.countBySeasonId(1L)).thenReturn(2L);

        service.runIfSeasonEnded();

        verify(rewardIssueService).issueUserReward(1L, 10L, League.GOLD);
        verify(rewardIssueService).issueUserReward(1L, 11L, League.BRONZE);
        verify(rewardIssueService).finalizeSeason(1L);
    }

    @DisplayName("오케스트레이션 — 한 유저 실패해도 나머지는 계속, 부분 완료면 종료 확정 안 함(다음 주기 재시도)")
    @Test
    void partialFailure_continuesAndDoesNotFinalize() {
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(1L);
        UserTrophy gold = trophy(10L, League.GOLD);
        UserTrophy bronze = trophy(11L, League.BRONZE);
        when(seasonRepository.findFirstUnprocessedEndedSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.of(season));
        when(userTrophyRepository.findAllBySeasonId(1L)).thenReturn(List.of(gold, bronze));
        doThrow(new RuntimeException("combat 다운"))
                .when(rewardIssueService)
                .issueUserReward(1L, 10L, League.GOLD);
        when(seasonRewardRepository.countBySeasonId(1L)).thenReturn(1L); // bronze만 지급됨

        service.runIfSeasonEnded();

        verify(rewardIssueService).issueUserReward(1L, 11L, League.BRONZE); // 다음 유저 계속
        verify(rewardIssueService, never()).finalizeSeason(anyLong()); // 부분 완료 → 종료 안 함
    }

    private UserTrophy trophy(long userId, League league) {
        UserTrophy trophy = mock(UserTrophy.class);
        when(trophy.getUserId()).thenReturn(userId);
        when(trophy.getLeague()).thenReturn(league);
        return trophy;
    }
}
