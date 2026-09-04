package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.dto.AdminBulkForceStartRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminBulkTerritoryAuctionRequest;
import com.territorial.auction.domain.admin.dto.AdminChangeGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminTerritoryResponse;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.global.client.MapAdminClient;
import com.territorial.auction.global.client.MapAdminClient.ChangeResult;
import com.territorial.auction.global.client.MapAdminClient.TerritoryView;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 관리자 영토 관리는 map-service로 위임된다(영토 상태·검증은 map-service가 소유). 여기서는 위임 호출·응답 매핑·감사 로그만 검증한다. */
@ExtendWith(MockitoExtension.class)
class AdminTerritoryServiceTest {

    @InjectMocks private AdminTerritoryService adminTerritoryService;

    @Mock private MapAdminClient mapAdminClient;
    @Mock private AdminAuditLogger adminAuditLogger;

    private TerritoryView view(Long id, String grade, String status, boolean auctionEnabled) {
        return new TerritoryView(id, 1, 2, grade, status, null, auctionEnabled);
    }

    @Test
    @DisplayName("등급 변경 → map 위임 + 감사 기록 + 새 등급 반영")
    void changeGrade_delegatesAndAudits() {
        given(mapAdminClient.changeGrade(100L, "S"))
                .willReturn(new ChangeResult("C", null, view(100L, "S", "IDLE", true)));

        AdminTerritoryResponse response =
                adminTerritoryService.changeGrade(1L, 100L, new AdminChangeGradeRequest("S", "조정"));

        assertThat(response.grade()).isEqualTo("S");
        then(mapAdminClient).should().changeGrade(100L, "S");
        then(adminAuditLogger)
                .should()
                .record(eq(1L), eq("TERRITORY_GRADE_CHANGE"), eq("TERRITORY"), eq(100L), any());
    }

    @Test
    @DisplayName("경매 토글 → map 위임 + 감사 기록 + auctionEnabled 반영")
    void changeAuctionEnabled_delegatesAndAudits() {
        given(mapAdminClient.changeAuctionEnabled(100L, false))
                .willReturn(new ChangeResult(null, true, view(100L, "C", "IDLE", false)));

        AdminTerritoryResponse response =
                adminTerritoryService.changeAuctionEnabled(
                        1L, 100L, new AdminToggleAuctionRequest(false, "중지"));

        assertThat(response.auctionEnabled()).isFalse();
        then(adminAuditLogger)
                .should()
                .record(eq(1L), eq("TERRITORY_AUCTION_TOGGLE"), eq("TERRITORY"), eq(100L), any());
    }

    @Test
    @DisplayName("강제 시작 → map 위임 + 감사 기록")
    void forceStart_delegatesAndAudits() {
        given(mapAdminClient.forceStartAuction(100L))
                .willReturn(new ChangeResult(null, null, view(100L, "C", "BIDDING", true)));

        AdminTerritoryResponse response = adminTerritoryService.forceStartAuction(1L, 100L);

        assertThat(response.status()).isEqualTo("BIDDING");
        then(adminAuditLogger)
                .should()
                .record(
                        eq(1L),
                        eq("TERRITORY_AUCTION_FORCE_START"),
                        eq("TERRITORY"),
                        eq(100L),
                        any());
    }

    @Test
    @DisplayName("대륙 영토 목록 → map 위임 결과를 응답으로 매핑")
    void getTerritories_delegates() {
        given(mapAdminClient.getContinentTerritories(5L))
                .willReturn(List.of(view(1L, "C", "IDLE", true), view(2L, "D", "OCCUPIED", true)));

        var response = adminTerritoryService.getTerritories(5L);

        assertThat(response.territories()).hasSize(2);
        assertThat(response.territories().get(0).territoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("일괄 등급 변경 → 변경된 개수 반환 + 항목별 감사")
    void bulkChangeGrade_delegatesAndAudits() {
        given(mapAdminClient.bulkChangeGrade("S", List.of(1L, 2L)))
                .willReturn(List.of(new ChangeResult("C", null, view(1L, "S", "IDLE", true))));

        AdminBulkResultResponse res =
                adminTerritoryService.bulkChangeGrade(
                        1L, new AdminBulkGradeRequest(List.of(1L, 2L), "S", "일괄"));

        assertThat(res.affected()).isEqualTo(1);
        then(adminAuditLogger)
                .should()
                .record(eq(1L), eq("TERRITORY_GRADE_CHANGE_BULK"), eq("TERRITORY"), eq(1L), any());
    }

    @Test
    @DisplayName("경매 활성 일괄 변경 → 변경된 개수 반환")
    void bulkChangeAuction_delegates() {
        given(mapAdminClient.bulkChangeAuction(false, List.of(1L, 2L)))
                .willReturn(
                        List.of(
                                new ChangeResult(null, true, view(1L, "C", "IDLE", false)),
                                new ChangeResult(null, true, view(2L, "C", "IDLE", false))));

        AdminBulkResultResponse res =
                adminTerritoryService.bulkChangeAuction(
                        1L, new AdminBulkTerritoryAuctionRequest(List.of(1L, 2L), false, "일괄중지"));

        assertThat(res.affected()).isEqualTo(2);
    }

    @Test
    @DisplayName("강제 시작 일괄 → 시작된 개수 반환")
    void bulkForceStart_delegates() {
        given(mapAdminClient.bulkForceStart(List.of(1L, 2L)))
                .willReturn(List.of(new ChangeResult(null, null, view(1L, "C", "BIDDING", true))));

        AdminBulkResultResponse res =
                adminTerritoryService.bulkForceStart(
                        1L, new AdminBulkForceStartRequest(List.of(1L, 2L), "일괄 시작"));

        assertThat(res.affected()).isEqualTo(1);
    }
}
