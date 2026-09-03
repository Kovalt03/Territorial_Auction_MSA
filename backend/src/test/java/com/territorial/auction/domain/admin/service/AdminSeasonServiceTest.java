package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.admin.client.SeasonAdminClient;
import com.territorial.auction.domain.admin.client.SeasonAdminClient.SeasonView;
import com.territorial.auction.domain.admin.dto.AdminCreateSeasonRequest;
import com.territorial.auction.domain.admin.dto.AdminSeasonResponse;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSeasonServiceTest {

    @InjectMocks private AdminSeasonService adminSeasonService;

    @Mock private SeasonAdminClient seasonAdminClient;
    @Mock private AdminAuditLogger adminAuditLogger;

    @Test
    @DisplayName("새 시즌 생성 성공 → season-service 위임 결과 매핑 + 감사 로그")
    void createSeason_success() {
        given(seasonAdminClient.createSeason(any(), any()))
                .willReturn(new SeasonView(99L, 4, LocalDateTime.now(), null, null));

        AdminSeasonResponse res =
                adminSeasonService.createSeason(10L, new AdminCreateSeasonRequest(null, null));

        assertThat(res.seasonNumber()).isEqualTo(4);
        assertThat(res.status()).isEqualTo("ACTIVE");
        then(adminAuditLogger).should().record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("진행 중 시즌 존재 시 season-service가 거부 → SEASON_ALREADY_ACTIVE 전파, 감사 로그 없음")
    void createSeason_activeExists() {
        given(seasonAdminClient.createSeason(any(), any()))
                .willThrow(new CustomException(ErrorCode.SEASON_ALREADY_ACTIVE));

        assertThatThrownBy(
                        () ->
                                adminSeasonService.createSeason(
                                        10L, new AdminCreateSeasonRequest(null, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_ALREADY_ACTIVE);
        then(adminAuditLogger).should(never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("시즌 종료 성공 → 위임 결과 매핑(ENDED) + 감사 로그")
    void endSeason_success() {
        given(seasonAdminClient.endSeason(5L))
                .willReturn(
                        new SeasonView(
                                5L, 3, LocalDateTime.now().minusDays(1), LocalDateTime.now(), null));

        AdminSeasonResponse res = adminSeasonService.endSeason(10L, 5L);

        assertThat(res.status()).isEqualTo("ENDED");
        then(adminAuditLogger).should().record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 종료된 시즌 → season-service가 SEASON_ALREADY_ENDED 전파")
    void endSeason_alreadyEnded() {
        given(seasonAdminClient.endSeason(5L))
                .willThrow(new CustomException(ErrorCode.SEASON_ALREADY_ENDED));

        assertThatThrownBy(() -> adminSeasonService.endSeason(10L, 5L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_ALREADY_ENDED);
    }
}
