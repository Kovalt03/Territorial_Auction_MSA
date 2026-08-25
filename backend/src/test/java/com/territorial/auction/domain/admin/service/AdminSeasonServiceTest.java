package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.admin.dto.AdminCreateSeasonRequest;
import com.territorial.auction.domain.admin.dto.AdminSeasonResponse;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminSeasonServiceTest {

    @InjectMocks private AdminSeasonService adminSeasonService;

    @Mock private SeasonRepository seasonRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private Season season(long id, int number, LocalDateTime startedAt, LocalDateTime endedAt) {
        Season s =
                Season.builder().seasonNumber(number).startedAt(startedAt).endedAt(endedAt).build();
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    @Test
    @DisplayName("새 시즌 생성 성공 → 번호 자동 증가, 즉시 시작")
    void createSeason_success() {
        given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());
        given(seasonRepository.findMaxSeasonNumber()).willReturn(3);
        given(seasonRepository.save(any()))
                .willAnswer(
                        inv -> {
                            Season s = inv.getArgument(0);
                            ReflectionTestUtils.setField(s, "id", 99L);
                            return s;
                        });

        AdminSeasonResponse res =
                adminSeasonService.createSeason(10L, new AdminCreateSeasonRequest(null, null));

        assertThat(res.seasonNumber()).isEqualTo(4);
        assertThat(res.status()).isEqualTo("ACTIVE");
        then(adminAuditLogger).should().record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("진행 중 시즌 존재 시 생성 거부 → SEASON_ALREADY_ACTIVE")
    void createSeason_activeExists() {
        given(seasonRepository.findActiveSeason(any()))
                .willReturn(Optional.of(season(1L, 3, LocalDateTime.now().minusDays(1), null)));

        assertThatThrownBy(
                        () ->
                                adminSeasonService.createSeason(
                                        10L, new AdminCreateSeasonRequest(null, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_ALREADY_ACTIVE);
        then(seasonRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("시즌 종료 성공 → endedAt 설정")
    void endSeason_success() {
        Season s = season(5L, 3, LocalDateTime.now().minusDays(1), null);
        given(seasonRepository.findById(5L)).willReturn(Optional.of(s));

        AdminSeasonResponse res = adminSeasonService.endSeason(10L, 5L);

        assertThat(s.getEndedAt()).isNotNull();
        assertThat(res.status()).isEqualTo("ENDED");
        then(adminAuditLogger).should().record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 종료된 시즌 → SEASON_ALREADY_ENDED")
    void endSeason_alreadyEnded() {
        Season s =
                season(5L, 3, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        given(seasonRepository.findById(5L)).willReturn(Optional.of(s));

        assertThatThrownBy(() -> adminSeasonService.endSeason(10L, 5L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_ALREADY_ENDED);
    }
}
