package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.domain.combat.client.CombatResourceClient;
import com.territorial.auction.domain.combat.client.CombatResourceClient.BuildingView;
import com.territorial.auction.domain.combat.client.CombatResourceClient.TerritoryStorageView;
import com.territorial.auction.domain.map.dto.GridMapResponse;
import com.territorial.auction.domain.map.dto.TerritoryDetailResponse;
import com.territorial.auction.domain.map.entity.ColorHistory;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.Territory.TerritoryStatus;
import com.territorial.auction.domain.map.entity.TerritoryAuctionStatus;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.ColorHistoryRepository;
import com.territorial.auction.domain.map.repository.TerritoryAuctionStatusRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MapServiceTest {

    @InjectMocks private MapService mapService;

    @Mock private TerritoryRepository territoryRepository;
    @Mock private TerritoryAuctionStatusRepository territoryAuctionStatusRepository;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private ColorHistoryRepository colorHistoryRepository;

    // ────────────────────────────────────────────────────────────────
    // Fixtures
    // ────────────────────────────────────────────────────────────────

    private TerritoryGrade gradeA() {
        TerritoryGrade g =
                TerritoryGrade.builder()
                        .grade("A")
                        .productionMultiplier(new BigDecimal("1.5"))
                        .auctionPriceMultiplier(new BigDecimal("2.0"))
                        .preBuiltCount(1)
                        .spawnRate(new BigDecimal("0.120"))
                        .gridSize(10)
                        .zone1Radius(2)
                        .zone2Radius(4)
                        .build();
        ReflectionTestUtils.setField(g, "id", 1L);
        return g;
    }

    private Continent continent() {
        Continent c =
                Continent.builder()
                        .name("붉은 사막")
                        .themeColor("#FF4444")
                        .displayName("붉은 사막")
                        .build();
        ReflectionTestUtils.setField(c, "id", 1L);
        return c;
    }

    private Territory territory(Long id, int x, int y) {
        Territory t =
                Territory.builder()
                        .coordX(x)
                        .coordY(y)
                        .continent(continent())
                        .grade(gradeA())
                        .build();
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private Territory occupiedTerritory(Long id, int x, int y) {
        Territory t = territory(id, x, y);
        User owner =
                User.builder()
                        .username("owner")
                        .email("owner@example.com")
                        .passwordHash("hash")
                        .nickname("영토주인")
                        .build();
        ReflectionTestUtils.setField(owner, "id", 10L);
        ReflectionTestUtils.setField(t, "owner", owner);
        ReflectionTestUtils.setField(t, "currentColor", "#FF4444");
        ReflectionTestUtils.setField(t, "status", TerritoryStatus.OCCUPIED);
        ReflectionTestUtils.setField(t, "occupiedUntil", LocalDateTime.now().plusHours(24));
        return t;
    }

    @BeforeEach
    void setUpCombatStorage() {
        org.mockito.Mockito.lenient()
                .when(
                        combatResourceClient.getTerritoryStorage(
                                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new TerritoryStorageView(List.of(), 0, 0));
    }

    private TerritoryAuctionStatus auctionStatus(Long territoryId) {
        return TerritoryAuctionStatus.builder()
                .territoryId(territoryId)
                .auctionId(1L)
                .currentPrice(100)
                .endAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    // ────────────────────────────────────────────────────────────────
    // getGridMap()
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getGridMap()")
    class GetGridMap {

        @Test
        @DisplayName("대륙 필터 없음 — 전체 영토 반환")
        void getGridMap_noFilter_returnsAll() {
            List<Territory> territories =
                    List.of(territory(1L, 0, 0), territory(2L, 1, 0), territory(3L, 2, 0));
            given(territoryRepository.findAllWithContinentAndGrade()).willReturn(territories);
            given(territoryAuctionStatusRepository.findByTerritoryIdInAndEndAtAfter(any(), any()))
                    .willReturn(List.of());

            GridMapResponse response = mapService.getGridMap(null);

            assertThat(response.mapSize()).isEqualTo(50);
            assertThat(response.territories()).hasSize(3);
        }

        @Test
        @DisplayName("대륙 필터 있음 — 해당 대륙 영토만 반환")
        void getGridMap_withContinentFilter_returnsFiltered() {
            List<Territory> filtered = List.of(territory(1L, 0, 0));
            given(territoryRepository.findAllByContinentId(1L)).willReturn(filtered);
            given(territoryAuctionStatusRepository.findByTerritoryIdInAndEndAtAfter(any(), any()))
                    .willReturn(List.of());

            GridMapResponse response = mapService.getGridMap(1L);

            assertThat(response.territories()).hasSize(1);
            assertThat(response.territories().get(0).continentId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("경매 중인 영토 — hasActiveAuction true")
        void getGridMap_hasActiveAuction_true() {
            Territory t = territory(1L, 0, 0);
            given(territoryRepository.findAllWithContinentAndGrade()).willReturn(List.of(t));
            given(territoryAuctionStatusRepository.findByTerritoryIdInAndEndAtAfter(any(), any()))
                    .willReturn(List.of(auctionStatus(1L)));

            GridMapResponse response = mapService.getGridMap(null);

            assertThat(response.territories().get(0).hasActiveAuction()).isTrue();
        }

        @Test
        @DisplayName("점유자 없는 영토 — ownerId, ownerNickname null")
        void getGridMap_noOwner_ownerFieldsNull() {
            Territory t = territory(1L, 0, 0);
            given(territoryRepository.findAllWithContinentAndGrade()).willReturn(List.of(t));
            given(territoryAuctionStatusRepository.findByTerritoryIdInAndEndAtAfter(any(), any()))
                    .willReturn(List.of());

            GridMapResponse response = mapService.getGridMap(null);

            assertThat(response.territories().get(0).ownerId()).isNull();
            assertThat(response.territories().get(0).ownerNickname()).isNull();
        }
    }

    // ────────────────────────────────────────────────────────────────
    // getTerritoryDetail()
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTerritoryDetail()")
    class GetTerritoryDetail {

        @Test
        @DisplayName("정상 조회 — 기본 필드 반환")
        void getTerritoryDetail_success() {
            Territory t = territory(1L, 3, 7);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(territoryAuctionStatusRepository.findByTerritoryIdAndEndAtAfter(any(), any()))
                    .willReturn(Optional.empty());

            TerritoryDetailResponse response = mapService.getTerritoryDetail(1L);

            assertThat(response.territoryId()).isEqualTo(1L);
            assertThat(response.coordX()).isEqualTo(3);
            assertThat(response.coordY()).isEqualTo(7);
            assertThat(response.continentName()).isEqualTo("붉은 사막");
            assertThat(response.grade()).isEqualTo("A");
        }

        @Test
        @DisplayName("점유자 있음 — owner 필드 반환")
        void getTerritoryDetail_withOwner() {
            Territory t = occupiedTerritory(1L, 0, 0);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(territoryAuctionStatusRepository.findByTerritoryIdAndEndAtAfter(any(), any()))
                    .willReturn(Optional.empty());

            TerritoryDetailResponse response = mapService.getTerritoryDetail(1L);

            assertThat(response.owner()).isNotNull();
            assertThat(response.owner().userId()).isEqualTo(10L);
            assertThat(response.owner().nickname()).isEqualTo("영토주인");
        }

        @Test
        @DisplayName("점유자 없음 — owner null")
        void getTerritoryDetail_noOwner_ownerNull() {
            Territory t = territory(1L, 0, 0);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(territoryAuctionStatusRepository.findByTerritoryIdAndEndAtAfter(any(), any()))
                    .willReturn(Optional.empty());

            TerritoryDetailResponse response = mapService.getTerritoryDetail(1L);

            assertThat(response.owner()).isNull();
        }

        @Test
        @DisplayName("건물 있음 — buildings 반환")
        void getTerritoryDetail_withBuildings() {
            Territory t = territory(1L, 0, 0);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(combatResourceClient.getTerritoryStorage(1L))
                    .willReturn(
                            new TerritoryStorageView(
                                    List.of(new BuildingView(1L, "CASTLE", 1, 800, 1000)),
                                    0,
                                    5000));
            given(territoryAuctionStatusRepository.findByTerritoryIdAndEndAtAfter(any(), any()))
                    .willReturn(Optional.empty());

            TerritoryDetailResponse response = mapService.getTerritoryDetail(1L);

            assertThat(response.buildings()).hasSize(1);
            assertThat(response.buildings().get(0).type()).isEqualTo("CASTLE");
            assertThat(response.buildings().get(0).hp()).isEqualTo(800);
            assertThat(response.buildings().get(0).maxHp()).isEqualTo(1000);
        }

        @Test
        @DisplayName("경매 진행 중 — auction 필드 반환")
        void getTerritoryDetail_withActiveAuction() {
            Territory t = territory(1L, 0, 0);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(territoryAuctionStatusRepository.findByTerritoryIdAndEndAtAfter(any(), any()))
                    .willReturn(Optional.of(auctionStatus(1L)));

            TerritoryDetailResponse response = mapService.getTerritoryDetail(1L);

            assertThat(response.auction()).isNotNull();
            assertThat(response.auction().currentPrice()).isEqualTo(100);
        }

        @Test
        @DisplayName("경매 없음 — auction null")
        void getTerritoryDetail_noAuction_auctionNull() {
            Territory t = territory(1L, 0, 0);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(territoryAuctionStatusRepository.findByTerritoryIdAndEndAtAfter(any(), any()))
                    .willReturn(Optional.empty());

            TerritoryDetailResponse response = mapService.getTerritoryDetail(1L);

            assertThat(response.auction()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 영토 — TERRITORY_NOT_FOUND 예외")
        void getTerritoryDetail_notFound_throwsException() {
            given(territoryRepository.findByIdWithDetails(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mapService.getTerritoryDetail(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // changeColor()
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changeColor()")
    class ChangeColor {

        @Test
        @DisplayName("정상 변경 — 색상 업데이트 및 이력 저장")
        void changeColor_success() {
            Territory t = occupiedTerritory(1L, 0, 0);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(colorHistoryRepository.countByTerritoryIdAndUserId(1L, 10L)).willReturn(0L);

            mapService.changeColor(1L, 10L, "#00FF00");

            assertThat(t.getCurrentColor()).isEqualTo("#00FF00");
            verify(colorHistoryRepository).save(any(ColorHistory.class));
        }

        @Test
        @DisplayName("변경 횟수 2회 — 정상 처리")
        void changeColor_secondChange_success() {
            Territory t = occupiedTerritory(1L, 0, 0);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(colorHistoryRepository.countByTerritoryIdAndUserId(1L, 10L)).willReturn(2L);

            mapService.changeColor(1L, 10L, "#0000FF");

            assertThat(t.getCurrentColor()).isEqualTo("#0000FF");
        }

        @Test
        @DisplayName("존재하지 않는 영토 — TERRITORY_NOT_FOUND 예외")
        void changeColor_territoryNotFound() {
            given(territoryRepository.findByIdWithDetails(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mapService.changeColor(999L, 10L, "#00FF00"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
        }

        @Test
        @DisplayName("점유자 없는 영토 — NOT_TERRITORY_OWNER 예외")
        void changeColor_noOwner_throwsNotOwner() {
            Territory t = territory(1L, 0, 0); // owner null
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));

            assertThatThrownBy(() -> mapService.changeColor(1L, 10L, "#00FF00"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("다른 유저가 요청 — NOT_TERRITORY_OWNER 예외")
        void changeColor_notOwner_throwsForbidden() {
            Territory t = occupiedTerritory(1L, 0, 0); // owner id = 10L
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));

            assertThatThrownBy(() -> mapService.changeColor(1L, 99L, "#00FF00"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("IDLE 상태 영토 — TERRITORY_NOT_OCCUPIED 예외")
        void changeColor_idleTerritory_throwsNotOccupied() {
            Territory t = territory(1L, 0, 0);
            User owner =
                    User.builder()
                            .username("owner")
                            .email("owner@example.com")
                            .passwordHash("hash")
                            .nickname("영토주인")
                            .build();
            ReflectionTestUtils.setField(owner, "id", 10L);
            ReflectionTestUtils.setField(t, "owner", owner);
            // status = IDLE (기본값)

            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));

            assertThatThrownBy(() -> mapService.changeColor(1L, 10L, "#00FF00"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_OCCUPIED);
        }

        @Test
        @DisplayName("점유 기간 만료 — TERRITORY_NOT_OCCUPIED 예외")
        void changeColor_expiredOccupation_throwsNotOccupied() {
            Territory t = occupiedTerritory(1L, 0, 0);
            ReflectionTestUtils.setField(t, "occupiedUntil", LocalDateTime.now().minusHours(1));
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));

            assertThatThrownBy(() -> mapService.changeColor(1L, 10L, "#00FF00"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_OCCUPIED);
        }

        @Test
        @DisplayName("변경 횟수 3회 초과 — COLOR_CHANGE_LIMIT_EXCEEDED 예외")
        void changeColor_limitExceeded_throwsException() {
            Territory t = occupiedTerritory(1L, 0, 0);
            given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
            given(colorHistoryRepository.countByTerritoryIdAndUserId(1L, 10L)).willReturn(3L);

            assertThatThrownBy(() -> mapService.changeColor(1L, 10L, "#00FF00"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COLOR_CHANGE_LIMIT_EXCEEDED);

            verify(colorHistoryRepository, never()).save(any());
        }
    }
}
