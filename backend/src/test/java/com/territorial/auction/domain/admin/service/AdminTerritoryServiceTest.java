package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.territorial.auction.domain.admin.dto.AdminBulkForceStartRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminBulkTerritoryAuctionRequest;
import com.territorial.auction.domain.admin.dto.AdminChangeGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminTerritoryResponse;
import com.territorial.auction.domain.auction.entity.Auction;
import com.territorial.auction.domain.auction.repository.AuctionBidRepository;
import com.territorial.auction.domain.auction.repository.AuctionRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.Territory.TerritoryStatus;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryGradeRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminTerritoryServiceTest {

    @InjectMocks private AdminTerritoryService adminTerritoryService;

    @Mock private TerritoryRepository territoryRepository;
    @Mock private TerritoryGradeRepository territoryGradeRepository;
    @Mock private ContinentRepository continentRepository;
    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionBidRepository auctionBidRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

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

    private Territory territory(long id, TerritoryGrade g) {
        Territory t = Territory.builder().coordX(1).coordY(2).grade(g).build();
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    @Test
    @DisplayName("등급 변경 성공 → 새 등급 반영")
    void changeGrade_success() {
        Territory t = territory(100L, grade("C"));
        given(territoryRepository.findById(100L)).willReturn(Optional.of(t));
        given(territoryGradeRepository.findByGrade("S")).willReturn(Optional.of(grade("S")));

        AdminTerritoryResponse response =
                adminTerritoryService.changeGrade(1L, 100L, new AdminChangeGradeRequest("S", "조정"));

        assertThat(response.grade()).isEqualTo("S");
        assertThat(t.getGrade().getGrade()).isEqualTo("S");
    }

    @Test
    @DisplayName("점유 중 영토 등급 변경 → TERRITORY_GRADE_LOCKED_OCCUPIED")
    void changeGrade_occupiedLocked() {
        Territory t = territory(100L, grade("C"));
        ReflectionTestUtils.setField(t, "status", TerritoryStatus.OCCUPIED);
        given(territoryRepository.findById(100L)).willReturn(Optional.of(t));

        assertThatThrownBy(
                        () ->
                                adminTerritoryService.changeGrade(
                                        1L, 100L, new AdminChangeGradeRequest("S", "조정")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TERRITORY_GRADE_LOCKED_OCCUPIED);
        assertThat(t.getGrade().getGrade()).isEqualTo("C");
    }

    @Test
    @DisplayName("일괄 등급 변경 → 점유 영토는 건너뛰고 변경된 개수만 반환")
    void bulkChangeGrade_skipsOccupied() {
        Territory idle = territory(1L, grade("C"));
        Territory occupied = territory(2L, grade("C"));
        ReflectionTestUtils.setField(occupied, "status", TerritoryStatus.OCCUPIED);
        given(territoryGradeRepository.findByGrade("S")).willReturn(Optional.of(grade("S")));
        given(territoryRepository.findById(1L)).willReturn(Optional.of(idle));
        given(territoryRepository.findById(2L)).willReturn(Optional.of(occupied));

        var res =
                adminTerritoryService.bulkChangeGrade(
                        1L, new AdminBulkGradeRequest(java.util.List.of(1L, 2L), "S", "일괄"));

        assertThat(res.affected()).isEqualTo(1);
        assertThat(idle.getGrade().getGrade()).isEqualTo("S");
        assertThat(occupied.getGrade().getGrade()).isEqualTo("C");
    }

    @Test
    @DisplayName("존재하지 않는 영토 → TERRITORY_NOT_FOUND")
    void changeGrade_territoryNotFound() {
        given(territoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                adminTerritoryService.changeGrade(
                                        1L, 999L, new AdminChangeGradeRequest("S", "조정")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 등급 → TERRITORY_GRADE_NOT_FOUND")
    void changeGrade_gradeNotFound() {
        given(territoryRepository.findById(100L))
                .willReturn(Optional.of(territory(100L, grade("C"))));
        given(territoryGradeRepository.findByGrade("Z")).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                adminTerritoryService.changeGrade(
                                        1L, 100L, new AdminChangeGradeRequest("Z", "조정")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TERRITORY_GRADE_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 대륙 목록 조회 → CONTINENT_NOT_FOUND")
    void getTerritories_continentNotFound() {
        given(continentRepository.existsById(5L)).willReturn(false);

        assertThatThrownBy(() -> adminTerritoryService.getTerritories(5L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTINENT_NOT_FOUND);
    }

    @Test
    @DisplayName("등급 일괄 변경 → 모든 영토에 반영")
    void bulkChangeGrade_success() {
        Territory t1 = territory(1L, grade("C"));
        Territory t2 = territory(2L, grade("D"));
        given(territoryGradeRepository.findByGrade("S")).willReturn(Optional.of(grade("S")));
        given(territoryRepository.findById(1L)).willReturn(Optional.of(t1));
        given(territoryRepository.findById(2L)).willReturn(Optional.of(t2));

        AdminBulkResultResponse res =
                adminTerritoryService.bulkChangeGrade(
                        1L, new AdminBulkGradeRequest(List.of(1L, 2L), "S", "일괄"));

        assertThat(res.affected()).isEqualTo(2);
        assertThat(t1.getGrade().getGrade()).isEqualTo("S");
        assertThat(t2.getGrade().getGrade()).isEqualTo("S");
    }

    @Test
    @DisplayName("경매 활성 일괄 변경 → 모든 영토 auctionEnabled 반영")
    void bulkChangeAuction_success() {
        Territory t1 = territory(1L, grade("C"));
        Territory t2 = territory(2L, grade("C"));
        given(territoryRepository.findById(1L)).willReturn(Optional.of(t1));
        given(territoryRepository.findById(2L)).willReturn(Optional.of(t2));

        AdminBulkResultResponse res =
                adminTerritoryService.bulkChangeAuction(
                        1L, new AdminBulkTerritoryAuctionRequest(List.of(1L, 2L), false, "일괄중지"));

        assertThat(res.affected()).isEqualTo(2);
        assertThat(t1.getAuctionEnabled()).isFalse();
        assertThat(t2.getAuctionEnabled()).isFalse();
    }

    @Test
    @DisplayName("강제 시작 일괄 → IDLE만 시작, 나머지는 건너뜀")
    void bulkForceStart_startsOnlyIdle() {
        Territory idle = territory(1L, grade("C")); // 기본 상태 IDLE
        Territory bidding = territory(2L, grade("C"));
        ReflectionTestUtils.setField(bidding, "status", TerritoryStatus.BIDDING);
        given(territoryRepository.findById(1L)).willReturn(Optional.of(idle));
        given(territoryRepository.findById(2L)).willReturn(Optional.of(bidding));
        Auction saved =
                Auction.builder()
                        .territory(idle)
                        .currentPrice(1000)
                        .startAt(LocalDateTime.now())
                        .endAt(LocalDateTime.now().plusHours(24))
                        .maxExtendUntil(LocalDateTime.now().plusHours(25))
                        .build();
        ReflectionTestUtils.setField(saved, "id", 500L);
        given(auctionRepository.save(any())).willReturn(saved);

        AdminBulkResultResponse res =
                adminTerritoryService.bulkForceStart(
                        1L, new AdminBulkForceStartRequest(List.of(1L, 2L), "일괄 시작"));

        assertThat(res.affected()).isEqualTo(1);
        assertThat(idle.getStatus()).isEqualTo(TerritoryStatus.BIDDING);
    }
}
