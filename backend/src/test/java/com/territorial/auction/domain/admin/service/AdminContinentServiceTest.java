package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminContinentCompositionResponse;
import com.territorial.auction.domain.admin.dto.AdminContinentCompositionResponse.ContinentComposition;
import com.territorial.auction.domain.admin.dto.AdminGradeDistributionRequest;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryGradeRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminContinentServiceTest {

    @InjectMocks private AdminContinentService adminContinentService;

    @Mock private ContinentRepository continentRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private TerritoryGradeRepository territoryGradeRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private Continent continent(long id, String name, Integer minTrophy) {
        Continent c =
                Continent.builder()
                        .name(name)
                        .themeColor("#fff")
                        .minTrophyRequired(minTrophy)
                        .build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private TerritoryGrade grade(String g) {
        return TerritoryGrade.builder()
                .grade(g)
                .productionMultiplier(BigDecimal.ONE)
                .auctionPriceMultiplier(BigDecimal.ONE)
                .preBuiltCount(0)
                .spawnRate(BigDecimal.ONE)
                .gridSize(10)
                .build();
    }

    private Territory territory(int x, int y, String g) {
        return Territory.builder().coordX(x).coordY(y).grade(grade(g)).build();
    }

    @Test
    @DisplayName("대륙별 등급·상태 집계를 조합해 구성 현황 반환")
    void getCompositions_success() {
        given(continentRepository.findAll())
                .willReturn(List.of(continent(1L, "글리치", 0), continent(2L, "네뷸라", 1000)));
        given(territoryRepository.aggregateCompositionGroupByContinent())
                .willReturn(
                        List.of(
                                new Object[] {1L, "S", Territory.TerritoryStatus.BIDDING, 2L},
                                new Object[] {1L, "A", Territory.TerritoryStatus.OCCUPIED, 5L},
                                new Object[] {1L, "A", Territory.TerritoryStatus.IDLE, 3L},
                                new Object[] {2L, "B", Territory.TerritoryStatus.IDLE, 4L}));

        AdminContinentCompositionResponse response = adminContinentService.getCompositions();

        assertThat(response.continents())
                .extracting(
                        ContinentComposition::continentId, ContinentComposition::totalTerritories)
                .containsExactly(tuple(1L, 10L), tuple(2L, 4L));

        ContinentComposition glitch = response.continents().get(0);
        assertThat(glitch.gradeBreakdown()).containsEntry("S", 2L).containsEntry("A", 8L);
        assertThat(glitch.biddingCount()).isEqualTo(2L);
        assertThat(glitch.occupiedCount()).isEqualTo(5L);
        assertThat(glitch.idleCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("영토 집계가 없는 대륙은 0으로 채워 반환")
    void getCompositions_emptyContinent() {
        given(continentRepository.findAll()).willReturn(List.of(continent(9L, "빈행성", 5000)));
        given(territoryRepository.aggregateCompositionGroupByContinent()).willReturn(List.of());

        AdminContinentCompositionResponse response = adminContinentService.getCompositions();

        ContinentComposition empty = response.continents().get(0);
        assertThat(empty.totalTerritories()).isZero();
        assertThat(empty.gradeBreakdown()).isEmpty();
        assertThat(empty.biddingCount()).isZero();
    }

    @Test
    @DisplayName("등급 분포 일괄 조정 → 목표 분포로 재배정 + 감사 로그")
    void applyGradeDistribution_success() {
        given(continentRepository.findById(1L))
                .willReturn(java.util.Optional.of(continent(1L, "글리치", 0)));
        given(territoryRepository.findAllByContinentIdWithDetails(1L))
                .willReturn(
                        List.of(territory(0, 0, "C"), territory(1, 0, "C"), territory(2, 0, "C")));
        given(territoryGradeRepository.findAll())
                .willReturn(List.of(grade("S"), grade("A"), grade("C")));

        ContinentComposition result =
                adminContinentService.applyGradeDistribution(
                        1L, 1L, new AdminGradeDistributionRequest(Map.of("S", 1, "A", 2), "조정"));

        assertThat(result.gradeBreakdown()).containsEntry("S", 1L).containsEntry("A", 2L);
        then(adminAuditLogger)
                .should()
                .record(eq(1L), eq("CONTINENT_GRADE_DISTRIBUTION"), eq("CONTINENT"), eq(1L), any());
    }

    @Test
    @DisplayName("분포 합계가 총 영토 수와 불일치 → GRADE_DISTRIBUTION_MISMATCH")
    void applyGradeDistribution_mismatch() {
        given(continentRepository.findById(1L))
                .willReturn(java.util.Optional.of(continent(1L, "글리치", 0)));
        given(territoryRepository.findAllByContinentIdWithDetails(1L))
                .willReturn(List.of(territory(0, 0, "C")));

        assertThatThrownBy(
                        () ->
                                adminContinentService.applyGradeDistribution(
                                        1L,
                                        1L,
                                        new AdminGradeDistributionRequest(Map.of("S", 5), "x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GRADE_DISTRIBUTION_MISMATCH);
    }

    @Test
    @DisplayName("대륙 경매 전체 비활성화 → 영향 받은 영토 수 반환")
    void changeContinentAuction_success() {
        given(continentRepository.existsById(1L)).willReturn(true);
        given(territoryRepository.updateAuctionEnabledByContinentId(1L, false)).willReturn(300);

        AdminBulkResultResponse res =
                adminContinentService.changeContinentAuction(
                        10L, 1L, new AdminToggleAuctionRequest(false, "행성 점검"));

        assertThat(res.affected()).isEqualTo(300);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("CONTINENT_AUCTION_TOGGLE"), eq("CONTINENT"), eq(1L), any());
    }

    @Test
    @DisplayName("없는 대륙 경매 토글 → CONTINENT_NOT_FOUND")
    void changeContinentAuction_notFound() {
        given(continentRepository.existsById(9L)).willReturn(false);

        assertThatThrownBy(
                        () ->
                                adminContinentService.changeContinentAuction(
                                        10L, 9L, new AdminToggleAuctionRequest(false, "x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTINENT_NOT_FOUND);
    }
}
