package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.dto.AdminSeasonPassResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateSeasonPassRequest;
import com.territorial.auction.domain.season.entity.SeasonPass;
import com.territorial.auction.domain.season.repository.SeasonPassRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminSeasonPassServiceTest {

    @InjectMocks private AdminSeasonPassService adminSeasonPassService;

    @Mock private SeasonPassRepository seasonPassRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private SeasonPass pass() {
        SeasonPass pass =
                SeasonPass.builder()
                        .name("시즌 패스 Vol.1")
                        .costAp(1000)
                        .durationDays(30)
                        .islandBonusPct(50)
                        .extraBuilders(1)
                        .taxExemptBonus(2)
                        .buildTimeReductionPct(20)
                        .build();
        ReflectionTestUtils.setField(pass, "id", 1L);
        return pass;
    }

    @Test
    @DisplayName("시즌 패스 수정 → 값 반영 + 감사 로그")
    void update_success() {
        given(seasonPassRepository.findById(1L)).willReturn(Optional.of(pass()));

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
    @DisplayName("없는 패스 수정 → SEASON_PASS_NOT_FOUND")
    void update_notFound() {
        given(seasonPassRepository.findById(9L)).willReturn(Optional.empty());

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
