package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminContinentCompositionResponse;
import com.territorial.auction.domain.admin.dto.AdminContinentCompositionResponse.ContinentComposition;
import com.territorial.auction.domain.admin.dto.AdminGradeDistributionRequest;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.global.client.MapAdminClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 관리자 대륙 관리는 map-service로 위임된다(집계·재분배·검증은 map-service가 소유). 여기서는 위임 호출·응답 매핑·감사 로그만 검증한다. */
@ExtendWith(MockitoExtension.class)
class AdminContinentServiceTest {

    @InjectMocks private AdminContinentService adminContinentService;

    @Mock private MapAdminClient mapAdminClient;
    @Mock private AdminAuditLogger adminAuditLogger;

    private MapAdminClient.ContinentComposition composition(
            Long id, String name, long total, Map<String, Long> breakdown) {
        return new MapAdminClient.ContinentComposition(
                id, name, 0, total, breakdown, 0L, 0L, total);
    }

    @Test
    @DisplayName("구성 현황 → map 위임 결과를 응답으로 매핑")
    void getCompositions_delegates() {
        given(mapAdminClient.getCompositions())
                .willReturn(
                        new MapAdminClient.CompositionResponse(
                                List.of(
                                        composition(1L, "글리치", 10L, Map.of("S", 2L, "A", 8L)),
                                        composition(2L, "네뷸라", 4L, Map.of("B", 4L)))));

        AdminContinentCompositionResponse response = adminContinentService.getCompositions();

        assertThat(response.continents()).hasSize(2);
        ContinentComposition glitch = response.continents().get(0);
        assertThat(glitch.continentId()).isEqualTo(1L);
        assertThat(glitch.totalTerritories()).isEqualTo(10L);
        assertThat(glitch.gradeBreakdown()).containsEntry("S", 2L).containsEntry("A", 8L);
    }

    @Test
    @DisplayName("등급 분포 조정 → map 위임 + 감사 로그 + 결과 매핑")
    void applyGradeDistribution_delegatesAndAudits() {
        Map<String, Integer> distribution = Map.of("S", 1, "A", 2);
        given(mapAdminClient.applyGradeDistribution(1L, distribution))
                .willReturn(
                        new MapAdminClient.GradeDistributionResult(
                                Map.of("C", 3L),
                                composition(1L, "글리치", 3L, Map.of("S", 1L, "A", 2L))));

        ContinentComposition result =
                adminContinentService.applyGradeDistribution(
                        1L, 1L, new AdminGradeDistributionRequest(distribution, "조정"));

        assertThat(result.gradeBreakdown()).containsEntry("S", 1L).containsEntry("A", 2L);
        then(adminAuditLogger)
                .should()
                .record(eq(1L), eq("CONTINENT_GRADE_DISTRIBUTION"), eq("CONTINENT"), eq(1L), any());
    }

    @Test
    @DisplayName("대륙 경매 전체 토글 → 영향 영토 수 반환 + 감사 로그")
    void changeContinentAuction_delegatesAndAudits() {
        given(mapAdminClient.changeContinentAuction(1L, false)).willReturn(300);

        AdminBulkResultResponse res =
                adminContinentService.changeContinentAuction(
                        10L, 1L, new AdminToggleAuctionRequest(false, "행성 점검"));

        assertThat(res.affected()).isEqualTo(300);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("CONTINENT_AUCTION_TOGGLE"), eq("CONTINENT"), eq(1L), any());
    }
}
