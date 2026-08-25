package com.territorial.auction.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.admin.repository.AdminSettingRepository;
import com.territorial.auction.domain.auction.AuctionPolicy;
import com.territorial.auction.domain.auction.entity.Auction;
import com.territorial.auction.domain.auction.entity.AuctionBid;
import com.territorial.auction.domain.auction.entity.AuctionHistory;
import com.territorial.auction.domain.auction.repository.AuctionBidRepository;
import com.territorial.auction.domain.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.domain.auction.repository.AuctionRepository;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.Territory.TerritoryStatus;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleServiceTest {

    @InjectMocks private AuctionLifecycleService lifecycleService;

    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionBidRepository auctionBidRepository;
    @Mock private AuctionHistoryRepository auctionHistoryRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private AdminSettingRepository adminSettingRepository;
    @Mock private com.territorial.auction.domain.admin.service.AdminAuditLogger adminAuditLogger;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.territorial.auction.domain.notification.service.NotificationService
            notificationService;

    @Mock private SimpMessagingTemplate messagingTemplate;

    @Captor private ArgumentCaptor<Auction> auctionCaptor;
    @Captor private ArgumentCaptor<AuctionBid> auctionBidCaptor;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        // 기본값: 낙찰 영토에 이미 성이 있다고 보고 자동 생성 경로를 건너뛴다.
        lenient()
                .when(buildingInstanceRepository.existsCastleOnTerritory(anyLong()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ─── 공통 픽스처 ─────────────────────────────────────────────────────────

    private User sampleUser(Long id) {
        User user =
                User.builder()
                        .username("user" + id)
                        .email("user" + id + "@example.com")
                        .passwordHash("encoded")
                        .nickname("User" + id)
                        .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Territory mockTerritory(Long id, String gradeCode) {
        TerritoryGrade grade = mock(TerritoryGrade.class);
        lenient().when(grade.getGrade()).thenReturn(gradeCode);

        Territory territory = mock(Territory.class);
        lenient().when(territory.getId()).thenReturn(id);
        lenient().when(territory.getGrade()).thenReturn(grade);
        return territory;
    }

    private Auction mockAuction(Long id, int price, User currentBidder, Territory territory) {
        Auction auction = mock(Auction.class);
        lenient().when(auction.getId()).thenReturn(id);
        lenient().when(auction.getCurrentPrice()).thenReturn(price);
        lenient().when(auction.getCurrentBidder()).thenReturn(currentBidder);
        lenient().when(auction.getTerritory()).thenReturn(territory);
        return auction;
    }

    // ─── settlePendingAuctions() ─────────────────────────────────────────────

    @Nested
    @DisplayName("settlePendingAuctions()")
    class SettlePendingAuctions {

        @Test
        @DisplayName("만료된 미정산 경매가 없으면 아무 작업도 하지 않음")
        void noExpiredAuctions_nothingHappens() {
            given(auctionRepository.findAllExpiredUnsettled(any()))
                    .willReturn(Collections.emptyList());

            lifecycleService.settlePendingAuctions();

            then(auctionHistoryRepository).should(never()).save(any());
            then(walletRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("낙찰자 있음 - 영토 점유, AP 차감, 낙찰 이력 저장, settled 처리")
        void withWinner_occupiesAndConsumesApAndSavesHistoryAndSettles() {
            User winner = sampleUser(10L);
            Wallet wallet = mock(Wallet.class);
            Territory territory = mockTerritory(5L, "A");
            Auction auction = mockAuction(1L, 3000, winner, territory);

            given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(auction));
            given(walletRepository.findByIdWithLock(10L)).willReturn(Optional.of(wallet));
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            lifecycleService.settlePendingAuctions();

            then(territory)
                    .should()
                    .occupy(eq(winner), any(LocalDateTime.class), any(LocalDateTime.class));
            then(wallet).should().consumeLockedAp(3000);
            then(auctionHistoryRepository).should().save(any(AuctionHistory.class));
            then(auction).should().settle();
        }

        @Test
        @DisplayName("낙찰자 있음 + 지갑 미존재 - AP 차감 생략하지만 점유·이력·정산은 정상 처리")
        void withWinner_walletAbsent_skipsConsumeLockedAp() {
            User winner = sampleUser(10L);
            Territory territory = mockTerritory(5L, "A");
            Auction auction = mockAuction(1L, 3000, winner, territory);

            given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(auction));
            given(walletRepository.findByIdWithLock(10L)).willReturn(Optional.empty());
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            lifecycleService.settlePendingAuctions();

            then(territory)
                    .should()
                    .occupy(eq(winner), any(LocalDateTime.class), any(LocalDateTime.class));
            then(auctionHistoryRepository).should().save(any(AuctionHistory.class));
            then(auction).should().settle();
        }

        @Test
        @DisplayName("낙찰자 있음 + 성 없음 - Zone1 중심에 성 자동 생성 (level 1, stored_gp 0)")
        void withWinner_noCastle_createsInitialCastle() {
            User winner = sampleUser(10L);
            TerritoryGrade grade = mock(TerritoryGrade.class);
            lenient().when(grade.getGridSize()).thenReturn(8); // 중심 = (8/2)-1 = 3
            Territory territory = mock(Territory.class);
            lenient().when(territory.getId()).thenReturn(5L);
            lenient().when(territory.getGrade()).thenReturn(grade);
            Auction auction = mockAuction(1L, 3000, winner, territory);

            BuildingType castleType = mock(BuildingType.class);
            lenient().when(castleType.getMaxHp()).thenReturn(100);

            given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(auction));
            given(walletRepository.findByIdWithLock(10L)).willReturn(Optional.empty());
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());
            given(buildingInstanceRepository.existsCastleOnTerritory(5L)).willReturn(false);
            given(buildingTypeRepository.findByName("CASTLE")).willReturn(Optional.of(castleType));

            lifecycleService.settlePendingAuctions();

            ArgumentCaptor<BuildingInstance> captor =
                    ArgumentCaptor.forClass(BuildingInstance.class);
            then(buildingInstanceRepository).should().save(captor.capture());
            BuildingInstance saved = captor.getValue();
            assertThat(saved.getBuildingType()).isEqualTo(castleType);
            assertThat(saved.getTerritory()).isEqualTo(territory);
            assertThat(saved.getPosX()).isEqualTo(3);
            assertThat(saved.getPosY()).isEqualTo(3);
            assertThat(saved.getZone()).isEqualTo(1);
            assertThat(saved.getLevel()).isEqualTo(1);
            assertThat(saved.getStoredGp()).isEqualTo(0);
        }

        @Test
        @DisplayName("무낙찰 - 재경매 딜레이 후 IDLE 전환, 낙찰 이력 미저장, settled 처리")
        void noWinner_releasesWithDelayAndSettlesWithoutHistory() {
            Territory territory = mockTerritory(5L, "A");
            Auction auction = mockAuction(1L, 1000, null, territory);

            given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(auction));

            lifecycleService.settlePendingAuctions();

            then(territory).should().release(any(LocalDateTime.class));
            then(auctionHistoryRepository).should(never()).save(any());
            then(walletRepository).should(never()).findById(any());
            then(auction).should().settle();
        }

        @Test
        @DisplayName("첫 번째 경매에서 예외 발생해도 두 번째 경매는 정상 정산")
        void exceptionInOne_continuesWithOther() {
            Auction failingAuction = mock(Auction.class);
            given(failingAuction.getId()).willReturn(1L);
            given(failingAuction.getTerritory()).willThrow(new RuntimeException("DB 오류"));

            User winner = sampleUser(20L);
            Wallet wallet = mock(Wallet.class);
            Territory territory = mockTerritory(6L, "B");
            Auction successAuction = mockAuction(2L, 2000, winner, territory);

            given(auctionRepository.findAllExpiredUnsettled(any()))
                    .willReturn(List.of(failingAuction, successAuction));
            given(walletRepository.findByIdWithLock(20L)).willReturn(Optional.of(wallet));
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            lifecycleService.settlePendingAuctions(); // 예외 전파되지 않아야 함

            then(successAuction).should().settle();
        }
    }

    // ─── releaseExpiredTerritories() ─────────────────────────────────────────

    @Nested
    @DisplayName("releaseExpiredTerritories()")
    class ReleaseExpiredTerritories {

        @Test
        @DisplayName("만료된 점유 영토가 없으면 아무 작업도 하지 않음")
        void noExpiredTerritories_nothingHappens() {
            given(territoryRepository.findAllExpiredOccupied(eq(TerritoryStatus.OCCUPIED), any()))
                    .willReturn(Collections.emptyList());
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            lifecycleService.releaseExpiredTerritories();

            then(territoryRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("만료된 영토에 release() 호출 - 즉시 재경매 가능하도록 현재 시각 전달")
        void expiredTerritory_callsRelease() {
            Territory territory = mockTerritory(5L, "A");
            given(territoryRepository.findAllExpiredOccupied(eq(TerritoryStatus.OCCUPIED), any()))
                    .willReturn(List.of(territory));
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            lifecycleService.releaseExpiredTerritories();

            then(territory).should().release(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("만료된 영토 여러 개 모두 release() 호출")
        void multipleExpiredTerritories_allReleased() {
            Territory territory1 = mockTerritory(1L, "S");
            Territory territory2 = mockTerritory(2L, "C");
            Territory territory3 = mockTerritory(3L, "D");
            given(territoryRepository.findAllExpiredOccupied(eq(TerritoryStatus.OCCUPIED), any()))
                    .willReturn(List.of(territory1, territory2, territory3));
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            lifecycleService.releaseExpiredTerritories();

            then(territory1).should().release(any(LocalDateTime.class));
            then(territory2).should().release(any(LocalDateTime.class));
            then(territory3).should().release(any(LocalDateTime.class));
        }
    }

    // ─── createPendingAuctions() ─────────────────────────────────────────────

    @Nested
    @DisplayName("createPendingAuctions()")
    class CreatePendingAuctions {

        @BeforeEach
        void enableGlobalAuction() {
            // 전역 경매 스위치 미설정 → 활성으로 간주
            given(adminSettingRepository.findBySettingKey(any())).willReturn(Optional.empty());
        }

        @Test
        @DisplayName("경매 생성 대기 영토가 없으면 아무 작업도 하지 않음")
        void noReadyTerritories_nothingHappens() {
            given(territoryRepository.findAllReadyForAuction(eq(TerritoryStatus.IDLE), any()))
                    .willReturn(Collections.emptyList());

            lifecycleService.createPendingAuctions();

            then(auctionRepository).should(never()).save(any());
            then(auctionBidRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("등급별 시작가 적용 - A등급 영토의 경매 시작가는 5000")
        void knownGrade_usesGradeStartPrice() {
            Territory territory = mockTerritory(5L, "A");
            given(territoryRepository.findAllReadyForAuction(eq(TerritoryStatus.IDLE), any()))
                    .willReturn(List.of(territory));

            lifecycleService.createPendingAuctions();

            then(auctionRepository).should().save(auctionCaptor.capture());
            assertThat(auctionCaptor.getValue().getCurrentPrice())
                    .isEqualTo(AuctionPolicy.GRADE_START_PRICES.get("A"));
        }

        @Test
        @DisplayName("미등록 등급 영토는 기본 시작가(DEFAULT_START_PRICE) 사용")
        void unknownGrade_usesDefaultStartPrice() {
            Territory territory = mockTerritory(5L, "Z"); // 미정의 등급
            given(territoryRepository.findAllReadyForAuction(eq(TerritoryStatus.IDLE), any()))
                    .willReturn(List.of(territory));

            lifecycleService.createPendingAuctions();

            then(auctionRepository).should().save(auctionCaptor.capture());
            assertThat(auctionCaptor.getValue().getCurrentPrice())
                    .isEqualTo(AuctionPolicy.DEFAULT_START_PRICE);
        }

        @Test
        @DisplayName("경매 생성 시 Auction 저장, 시작가 초기 AuctionBid(bidder=null) 저장, startBidding() 호출")
        void createsAuctionAndInitialBidAndCallsStartBidding() {
            Territory territory = mockTerritory(5L, "B");
            given(territoryRepository.findAllReadyForAuction(eq(TerritoryStatus.IDLE), any()))
                    .willReturn(List.of(territory));

            lifecycleService.createPendingAuctions();

            then(auctionRepository).should().save(any(Auction.class));

            then(auctionBidRepository).should().save(auctionBidCaptor.capture());
            AuctionBid initialBid = auctionBidCaptor.getValue();
            assertThat(initialBid.getBidder()).isNull();
            assertThat(initialBid.getPrice()).isEqualTo(AuctionPolicy.GRADE_START_PRICES.get("B"));

            then(territory).should().startBidding();
        }

        @Test
        @DisplayName("생성된 경매의 종료 시각은 생성 시각 + 24시간")
        void createdAuction_endAtIs24HoursLater() {
            Territory territory = mockTerritory(5L, "C");
            given(territoryRepository.findAllReadyForAuction(eq(TerritoryStatus.IDLE), any()))
                    .willReturn(List.of(territory));

            LocalDateTime before = LocalDateTime.now();
            lifecycleService.createPendingAuctions();
            LocalDateTime after = LocalDateTime.now();

            then(auctionRepository).should().save(auctionCaptor.capture());
            Auction created = auctionCaptor.getValue();
            assertThat(created.getEndAt())
                    .isAfterOrEqualTo(before.plusHours(AuctionPolicy.AUCTION_DURATION_HOURS))
                    .isBeforeOrEqualTo(after.plusHours(AuctionPolicy.AUCTION_DURATION_HOURS));
        }

        @Test
        @DisplayName("첫 번째 영토에서 예외 발생해도 두 번째 영토는 경매 생성 처리")
        void exceptionInOne_continuesWithOther() {
            Territory failingTerritory = mock(Territory.class);
            given(failingTerritory.getId()).willReturn(1L);
            given(failingTerritory.getGrade()).willThrow(new RuntimeException("grade 조회 실패"));

            Territory successTerritory = mockTerritory(2L, "A");
            given(territoryRepository.findAllReadyForAuction(eq(TerritoryStatus.IDLE), any()))
                    .willReturn(List.of(failingTerritory, successTerritory));

            lifecycleService.createPendingAuctions(); // 예외 전파되지 않아야 함

            then(successTerritory).should().startBidding();
        }
    }

    // ─── 관리자 강제 종료 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("forceSettle() / forceCancel()")
    class ForceEnd {

        @Test
        @DisplayName("강제 낙찰 - 입찰자 있으면 정산 + 감사 로그")
        void forceSettle_withBidder() {
            User winner = sampleUser(10L);
            Wallet wallet = mock(Wallet.class);
            Territory territory = mockTerritory(5L, "A");
            Auction auction = mockAuction(1L, 3000, winner, territory);
            given(auction.isSettled()).willReturn(false);
            given(auctionRepository.findByIdWithDetails(1L)).willReturn(Optional.of(auction));
            given(walletRepository.findByIdWithLock(10L)).willReturn(Optional.of(wallet));
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            lifecycleService.forceSettle(99L, 1L);

            then(territory)
                    .should()
                    .occupy(eq(winner), any(LocalDateTime.class), any(LocalDateTime.class));
            then(auction).should().settle();
            then(adminAuditLogger)
                    .should()
                    .record(eq(99L), eq("AUCTION_FORCE_SETTLE"), eq("AUCTION"), eq(1L), any());
        }

        @Test
        @DisplayName("강제 낙찰 - 입찰자 없으면 AUCTION_NO_BIDDER_TO_SETTLE")
        void forceSettle_noBidder() {
            Territory territory = mockTerritory(5L, "A");
            Auction auction = mockAuction(1L, 1000, null, territory);
            given(auction.isSettled()).willReturn(false);
            given(auctionRepository.findByIdWithDetails(1L)).willReturn(Optional.of(auction));

            assertThatThrownBy(() -> lifecycleService.forceSettle(99L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NO_BIDDER_TO_SETTLE);
        }

        @Test
        @DisplayName("이미 정산된 경매 강제 종료 → AUCTION_ALREADY_SETTLED")
        void force_alreadySettled() {
            Territory territory = mockTerritory(5L, "A");
            Auction auction = mockAuction(1L, 1000, null, territory);
            given(auction.isSettled()).willReturn(true);
            given(auctionRepository.findByIdWithDetails(1L)).willReturn(Optional.of(auction));

            assertThatThrownBy(() -> lifecycleService.forceCancel(99L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_ALREADY_SETTLED);
        }

        @Test
        @DisplayName("강제 취소 - 입찰자 AP 환불 + 영토 IDLE 복귀 + 정산 + 감사 로그")
        void forceCancel_refundsAndReleases() {
            User bidder = sampleUser(10L);
            Wallet wallet = mock(Wallet.class);
            Territory territory = mockTerritory(5L, "A");
            given(territory.getCoordX()).willReturn(1);
            given(territory.getCoordY()).willReturn(2);
            Auction auction = mockAuction(1L, 3000, bidder, territory);
            given(auction.isSettled()).willReturn(false);
            given(auctionRepository.findByIdWithDetails(1L)).willReturn(Optional.of(auction));
            given(walletRepository.findByIdWithLock(10L)).willReturn(Optional.of(wallet));

            lifecycleService.forceCancel(99L, 1L);

            then(wallet).should().refundLockedAp(3000);
            then(territory).should().release(any(LocalDateTime.class));
            then(auction).should().settle();
            then(adminAuditLogger)
                    .should()
                    .record(eq(99L), eq("AUCTION_FORCE_CANCEL"), eq("AUCTION"), eq(1L), any());
        }
    }
}
