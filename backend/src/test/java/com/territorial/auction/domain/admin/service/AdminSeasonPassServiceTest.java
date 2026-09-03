package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.client.SeasonAdminClient;
import com.territorial.auction.domain.admin.client.SeasonAdminClient.SeasonPassView;
import com.territorial.auction.domain.admin.client.SeasonAdminClient.UpdateSeasonPassCommand;
import com.territorial.auction.domain.admin.dto.AdminSeasonPassResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateSeasonPassRequest;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSeasonPassServiceTest {

    @InjectMocks private AdminSeasonPassService adminSeasonPassService;

    @Mock private SeasonAdminClient seasonAdminClient;
    @Mock private AdminAuditLogger adminAuditLogger;

    @Test
    @DisplayName("시즌 패스 수정 → season-service 위임 결과 매핑 + 감사 로그")
    void update_success() {
        given(seasonAdminClient.updateSeasonPass(eq(1L), any(UpdateSeasonPassCommand.class)))
                .willReturn(new SeasonPassView(1L, "시즌 패스 Vol.1", 2000, 60, 80, 2, 3, 35));

        AdminSeasonPassResponse res =
                adminSeasonPassService.update(
                        10L, 1L, new AdminUpdateSeasonPassRequest(2000, 60, 80, 2, 3, 35));

        assertThat(res.costAp()).isEqualTo(2000);
        assertThat(res.extraBuilders()).isEqualTo(2);
        assertThat(res.buildTimeReductionPct()).isEqualTo(35);
        assertThat(res.name()).isEqualTo("시즌 패스 Vol.1"); // 이름은 변경 불가
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("SEASON_PASS_UPDATE"), any(), any(), any());
    }

    @Test
    @DisplayName("없는 패스 수정 → season-service가 SEASON_PASS_NOT_FOUND 전파")
    void update_notFound() {
        given(seasonAdminClient.updateSeasonPass(eq(9L), any(UpdateSeasonPassCommand.class)))
                .willThrow(new CustomException(ErrorCode.SEASON_PASS_NOT_FOUND));

        assertThatThrownBy(
                        () ->
                                adminSeasonPassService.update(
                                        10L,
                                        9L,
                                        new AdminUpdateSeasonPassRequest(1000, 30, 50, 1, 2, 20)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_PASS_NOT_FOUND);
    }
}
