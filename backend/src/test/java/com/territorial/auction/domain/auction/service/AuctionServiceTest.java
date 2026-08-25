package com.territorial.auction.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.auction.dto.AuctionBidHistoryResponse;
import com.territorial.auction.domain.auction.dto.AuctionDetailResponse;
import com.territorial.auction.domain.auction.dto.AuctionListResponse;
import com.territorial.auction.domain.auction.dto.MyBidListResponse;
import com.territorial.auction.domain.auction.dto.PlaceBidRequest;
import com.territorial.auction.domain.auction.dto.PlaceBidResponse;
import com.territorial.auction.domain.auction.dto.TerritoryAuctionHistoryResponse;
import com.territorial.auction.domain.auction.entity.Auction;
import com.territorial.auction.domain.auction.entity.AuctionBid;
import com.territorial.auction.domain.auction.entity.AuctionHistory;
import com.territorial.auction.domain.auction.entity.AuctionStatus;
import com.territorial.auction.domain.auction.repository.AuctionBidRepository;
import com.territorial.auction.domain.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.domain.auction.repository.AuctionRepository;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.UserRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @InjectMocks private AuctionService auctionService;

    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionBidRepository auctionBidRepository;
    @Mock private AuctionHistoryRepository auctionHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private NotificationService notificationService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void initTxSync() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTxSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ─── 공통 픽스처 ─────────────────────────────────────────────────────────

    private User sampleUser(Long id, String nickname) {
        User user =
                User.builder()
                        .username("user" + id)
                        .email("user" + id + "@example.com")
                        .passwordHash("encoded")
                        .nickname(nickname)
                        .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Wallet sampleWallet(User user, int availableAp) {
        Wallet wallet = Wallet.builder().user(user).build();
        ReflectionTestUtils.setField(wallet, "availableAp", availableAp);
        ReflectionTestUtils.setField(wallet, "lockedAp", 0);
        return wallet;
    }

    private Auction mockAuction(
            Long id,
            Integer currentPrice,
            User currentBidder,
            LocalDateTime endAt,
            LocalDateTime maxExtendUntil) {
        Auction auction = mock(Auction.class);
        // 공유 픽스처 — 각 테스트에서 필요한 스텁만 호출되므로 모두 lenient
        lenient().when(auction.getId()).thenReturn(id);
        lenient().when(auction.getCurrentPrice()).thenReturn(currentPrice);
        lenient().when(auction.getCurrentBidder()).thenReturn(currentBidder);
        lenient().when(auction.getEndAt()).thenReturn(endAt);
        lenient().when(auction.getMaxExtendUntil()).thenReturn(maxExtendUntil);
        lenient().when(auction.getStartAt()).thenReturn(LocalDateTime.now().minusHours(1));

        TerritoryGrade grade = mock(TerritoryGrade.class);
        lenient().when(grade.getGrade()).thenReturn("A");

        Continent continent = mock(Continent.class);
        lenient().when(continent.getName()).thenReturn("북부 대륙");
        lenient().when(continent.getDisplayName()).thenReturn("북부 대륙");

        Territory territory = mock(Territory.class);
        lenient().when(territory.getId()).thenReturn(5L);
        lenient().when(territory.getCoordX()).thenReturn(2);
        lenient().when(territory.getCoordY()).thenReturn(3);
        lenient().when(territory.getGrade()).thenReturn(grade);
        lenient().when(territory.getContinent()).thenReturn(continent);

        lenient().when(auction.getTerritory()).thenReturn(territory);
        return auction;
    }

    // ─── getAuctions() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAuctions()")
    class GetAuctions {

        @Test
        @DisplayName("필터 없이 조회 시 페이지네이션 응답 반환")
        void getAuctions_noFilter_success() {
            Auction auction =
                    mockAuction(
                            1L,
                            2000,
                            null,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            PageRequest pageable = PageRequest.of(0, 20);
            Page<Auction> page = new PageImpl<>(List.of(auction), pageable, 1);

            given(
                            auctionRepository.findAllWithFilter(
                                    isNull(), isNull(), any(LocalDateTime.class), eq(pageable)))
                    .willReturn(page);

            AuctionListResponse response = auctionService.getAuctions(null, null, pageable);

            assertThat(response.totalCount()).isEqualTo(1);
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(20);
            assertThat(response.auctions()).hasSize(1);

            AuctionListResponse.AuctionItemDto item = response.auctions().get(0);
            assertThat(item.auctionId()).isEqualTo(1L);
            assertThat(item.territoryId()).isEqualTo(5L);
            assertThat(item.coordX()).isEqualTo(2);
            assertThat(item.coordY()).isEqualTo(3);
            assertThat(item.continentName()).isEqualTo("북부 대륙");
            assertThat(item.grade()).isEqualTo("A");
            assertThat(item.currentPrice()).isEqualTo(2000);
            assertThat(item.currentBidderNickname()).isNull();
            assertThat(item.status()).isEqualTo(AuctionStatus.BIDDING);
        }

        @Test
        @DisplayName("continentId 필터 적용 시 해당 파라미터로 repository 호출")
        void getAuctions_withContinentFilter() {
            PageRequest pageable = PageRequest.of(0, 20);
            given(auctionRepository.findAllWithFilter(any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            auctionService.getAuctions(1L, null, pageable);

            then(auctionRepository)
                    .should()
                    .findAllWithFilter(
                            org.mockito.ArgumentMatchers.eq(1L),
                            org.mockito.ArgumentMatchers.isNull(),
                            any(LocalDateTime.class),
                            org.mockito.ArgumentMatchers.eq(pageable));
        }

        @Test
        @DisplayName("status=BIDDING 필터 적용 시 해당 파라미터로 repository 호출")
        void getAuctions_withStatusFilter() {
            PageRequest pageable = PageRequest.of(0, 20);
            given(auctionRepository.findAllWithFilter(any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            auctionService.getAuctions(null, AuctionStatus.BIDDING, pageable);

            then(auctionRepository)
                    .should()
                    .findAllWithFilter(
                            org.mockito.ArgumentMatchers.isNull(),
                            org.mockito.ArgumentMatchers.eq("BIDDING"),
                            any(LocalDateTime.class),
                            org.mockito.ArgumentMatchers.eq(pageable));
        }

        @Test
        @DisplayName("경매 없으면 totalCount=0, 빈 목록 반환")
        void getAuctions_empty() {
            PageRequest pageable = PageRequest.of(0, 20);
            given(auctionRepository.findAllWithFilter(any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            AuctionListResponse response = auctionService.getAuctions(null, null, pageable);

            assertThat(response.totalCount()).isEqualTo(0);
            assertThat(response.auctions()).isEmpty();
        }

        @Test
        @DisplayName("현재 최고 입찰자가 있으면 currentBidderNickname 포함")
        void getAuctions_withCurrentBidder() {
            User bidder = sampleUser(2L, "입찰왕");
            Auction auction =
                    mockAuction(
                            1L,
                            2000,
                            bidder,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            PageRequest pageable = PageRequest.of(0, 20);
            given(auctionRepository.findAllWithFilter(any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(auction), pageable, 1));

            AuctionListResponse response = auctionService.getAuctions(null, null, pageable);

            assertThat(response.auctions().get(0).currentBidderNickname()).isEqualTo("입찰왕");
        }

        @Test
        @DisplayName("종료된 경매는 status=IDLE")
        void getAuctions_endedAuction_statusIdle() {
            Auction auction =
                    mockAuction(
                            1L, 2000, null, LocalDateTime.now().minusHours(1), LocalDateTime.now());
            PageRequest pageable = PageRequest.of(0, 20);
            given(auctionRepository.findAllWithFilter(any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(auction), pageable, 1));

            AuctionListResponse response =
                    auctionService.getAuctions(null, AuctionStatus.IDLE, pageable);

            assertThat(response.auctions().get(0).status()).isEqualTo(AuctionStatus.IDLE);
        }
    }

    // ─── getAuctionDetail() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getAuctionDetail()")
    class GetAuctionDetail {

        @Test
        @DisplayName("정상 조회 시 recentBids 포함한 상세 응답 반환")
        void getAuctionDetail_success() {
            User bidder = sampleUser(2L, "입찰왕");
            Auction auction =
                    mockAuction(
                            1L,
                            2000,
                            bidder,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));

            AuctionBid bid = mock(AuctionBid.class);
            given(bid.getBidder()).willReturn(bidder);
            given(bid.getPrice()).willReturn(2000);
            given(bid.getBidAt()).willReturn(LocalDateTime.now().minusMinutes(5));

            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(auctionBidRepository.findTop5ByAuctionIdOrderByBidAtDesc(1L))
                    .willReturn(List.of(bid));

            AuctionDetailResponse response = auctionService.getAuctionDetail(1L);

            assertThat(response.auctionId()).isEqualTo(1L);
            assertThat(response.territoryId()).isEqualTo(5L);
            assertThat(response.coordX()).isEqualTo(2);
            assertThat(response.coordY()).isEqualTo(3);
            assertThat(response.grade()).isEqualTo("A");
            assertThat(response.currentPrice()).isEqualTo(2000);
            assertThat(response.currentBidderNickname()).isEqualTo("입찰왕");
            assertThat(response.recentBids()).hasSize(1);
            assertThat(response.recentBids().get(0).bidderNickname()).isEqualTo("입찰왕");
            assertThat(response.recentBids().get(0).price()).isEqualTo(2000);
        }

        @Test
        @DisplayName("입찰 내역이 없으면 recentBids 빈 리스트")
        void getAuctionDetail_noBids() {
            Auction auction =
                    mockAuction(
                            1L,
                            1000,
                            null,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(auctionBidRepository.findTop5ByAuctionIdOrderByBidAtDesc(1L))
                    .willReturn(Collections.emptyList());

            AuctionDetailResponse response = auctionService.getAuctionDetail(1L);

            assertThat(response.recentBids()).isEmpty();
            assertThat(response.currentBidderNickname()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 auctionId 시 AUCTION_NOT_FOUND 예외")
        void getAuctionDetail_notFound() {
            given(auctionRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> auctionService.getAuctionDetail(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);
        }
    }

    // ─── placeBid() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("placeBid()")
    class PlaceBid {

        // 입찰 검증: bidAmount >= currentPrice * 1.05 AND bidAmount >= currentPrice + 10 동시 만족
        // currentPrice=1000 → 최소 입찰가: max(1050, 1010) = 1050

        @Test
        @DisplayName("정상 입찰 시 AP 잠금, AuctionBid 저장, PlaceBidResponse 반환")
        void placeBid_success() {
            User bidder = sampleUser(1L, "입찰자");
            Wallet bidderWallet = sampleWallet(bidder, 5000); // availableAp=5000
            Auction auction =
                    mockAuction(
                            10L,
                            1000,
                            null,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));

            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
            given(userRepository.findById(1L)).willReturn(Optional.of(bidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(bidderWallet));
            given(auctionBidRepository.save(any(AuctionBid.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            PlaceBidRequest request =
                    new PlaceBidRequest(1100); // 1000 * 1.05 = 1050, 1000+10=1010 → 1100 OK
            PlaceBidResponse response = auctionService.placeBid(1L, 10L, request);

            // AP 잠금 검증: availableAp 감소, lockedAp 증가
            assertThat(bidderWallet.getAvailableAp()).isEqualTo(3900); // 5000 - 1100
            assertThat(bidderWallet.getLockedAp()).isEqualTo(1100);

            // Auction 업데이트 검증
            then(auction).should().updateBid(bidder, 1100);

            // AuctionBid 저장 검증
            then(auctionBidRepository).should().save(any(AuctionBid.class));

            // 응답 검증
            assertThat(response.auctionId()).isEqualTo(10L);
            assertThat(response.newPrice()).isEqualTo(1100);
        }

        @Test
        @DisplayName("종료 1분 전 입찰 시 endAt 30초 연장 (anti-sniping)")
        void placeBid_antiSniping_extendsEndAt() {
            User bidder = sampleUser(1L, "입찰자");
            Wallet bidderWallet = sampleWallet(bidder, 5000);

            // 종료 30초 전 = 1분 이내
            LocalDateTime endAt = LocalDateTime.now().plusSeconds(30);
            LocalDateTime maxExtendUntil = LocalDateTime.now().plusMinutes(30);
            Auction auction = mockAuction(10L, 1000, null, endAt, maxExtendUntil);

            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
            given(userRepository.findById(1L)).willReturn(Optional.of(bidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(bidderWallet));
            given(auctionBidRepository.save(any(AuctionBid.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            auctionService.placeBid(1L, 10L, new PlaceBidRequest(1100));

            // endAt + 30초 연장 호출 검증
            then(auction).should().extendEndAt(endAt.plusSeconds(30));
        }

        @Test
        @DisplayName("anti-sniping 연장 시 maxExtendUntil 초과하면 maxExtendUntil로 캡")
        void placeBid_antiSniping_cappedAtMaxExtendUntil() {
            User bidder = sampleUser(1L, "입찰자");
            Wallet bidderWallet = sampleWallet(bidder, 5000);

            LocalDateTime endAt = LocalDateTime.now().plusSeconds(30);
            // maxExtendUntil이 endAt + 15초 = 연장 후(+30초)보다 작음
            LocalDateTime maxExtendUntil = endAt.plusSeconds(15);
            Auction auction = mockAuction(10L, 1000, null, endAt, maxExtendUntil);

            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
            given(userRepository.findById(1L)).willReturn(Optional.of(bidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(bidderWallet));
            given(auctionBidRepository.save(any(AuctionBid.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            auctionService.placeBid(1L, 10L, new PlaceBidRequest(1100));

            // extendEndAt 호출 시 maxExtendUntil 전달 → 엔티티 내에서 cap 처리
            then(auction).should().extendEndAt(endAt.plusSeconds(30));
        }

        @Test
        @DisplayName("기존 최고 입찰자 있으면 해당 유저 lockedAp 환불")
        void placeBid_refundsPreviousBidder() {
            User prevBidder = sampleUser(2L, "전입찰자");
            Wallet prevWallet = sampleWallet(prevBidder, 0);
            ReflectionTestUtils.setField(prevWallet, "lockedAp", 1000); // 기존 입찰 잠금

            User newBidder = sampleUser(1L, "새입찰자");
            Wallet newWallet = sampleWallet(newBidder, 5000);

            Auction auction =
                    mockAuction(
                            10L,
                            1000,
                            prevBidder,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));

            AuctionBid prevBid = mock(AuctionBid.class);
            given(prevBid.getPrice()).willReturn(1000);

            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
            given(userRepository.findById(1L)).willReturn(Optional.of(newBidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(newWallet));
            given(walletRepository.findByIdWithLock(2L)).willReturn(Optional.of(prevWallet));
            given(auctionBidRepository.findTopByAuctionIdAndBidderIdOrderByPriceDesc(10L, 2L))
                    .willReturn(Optional.of(prevBid));
            given(auctionBidRepository.save(any(AuctionBid.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            auctionService.placeBid(1L, 10L, new PlaceBidRequest(1100));

            // 이전 입찰자 환불 검증
            assertThat(prevWallet.getLockedAp()).isEqualTo(0);
            assertThat(prevWallet.getAvailableAp()).isEqualTo(1000);
            var lockOrder = inOrder(walletRepository);
            lockOrder.verify(walletRepository).findByIdWithLock(1L);
            lockOrder.verify(walletRepository).findByIdWithLock(2L);
        }

        @Test
        @DisplayName("이전 입찰자 ID가 더 작아도 Wallet을 ID 오름차순으로 잠금")
        void placeBid_locksWalletsInAscendingUserIdOrder() {
            User prevBidder = sampleUser(1L, "전입찰자");
            Wallet prevWallet = sampleWallet(prevBidder, 0);
            ReflectionTestUtils.setField(prevWallet, "lockedAp", 1000);
            User newBidder = sampleUser(2L, "새입찰자");
            Wallet newWallet = sampleWallet(newBidder, 5000);
            Auction auction =
                    mockAuction(
                            10L,
                            1000,
                            prevBidder,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            AuctionBid prevBid = mock(AuctionBid.class);
            given(prevBid.getPrice()).willReturn(1000);
            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
            given(userRepository.findById(2L)).willReturn(Optional.of(newBidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(prevWallet));
            given(walletRepository.findByIdWithLock(2L)).willReturn(Optional.of(newWallet));
            given(auctionBidRepository.findTopByAuctionIdAndBidderIdOrderByPriceDesc(10L, 1L))
                    .willReturn(Optional.of(prevBid));

            auctionService.placeBid(2L, 10L, new PlaceBidRequest(1100));

            var lockOrder = inOrder(walletRepository);
            lockOrder.verify(walletRepository).findByIdWithLock(1L);
            lockOrder.verify(walletRepository).findByIdWithLock(2L);
        }

        @Test
        @DisplayName("존재하지 않는 auctionId 시 AUCTION_NOT_FOUND 예외")
        void placeBid_auctionNotFound() {
            given(auctionRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> auctionService.placeBid(1L, 99L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 종료된 경매 입찰 시 AUCTION_ALREADY_ENDED 예외")
        void placeBid_auctionEnded() {
            Auction auction =
                    mockAuction(
                            10L,
                            1000,
                            null,
                            LocalDateTime.now().minusSeconds(1),
                            LocalDateTime.now());
            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));

            assertThatThrownBy(() -> auctionService.placeBid(1L, 10L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_ALREADY_ENDED);
        }

        @Test
        @DisplayName("이미 최고 입찰자인 경우 ALREADY_HIGHEST_BIDDER 예외")
        void placeBid_alreadyHighestBidder() {
            User bidder = sampleUser(1L, "입찰자");
            Auction auction =
                    mockAuction(
                            10L,
                            1000,
                            bidder,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));

            assertThatThrownBy(() -> auctionService.placeBid(1L, 10L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ALREADY_HIGHEST_BIDDER);
        }

        @Test
        @DisplayName("입찰 금액이 5% 미만인 경우 BID_AMOUNT_TOO_LOW 예외")
        void placeBid_bidAmountBelowPercentThreshold() {
            // currentPrice=1000 → 최소 5% 이상: 1050 / 최소 +10: 1010 → 최소 1050
            Auction auction =
                    mockAuction(
                            10L,
                            1000,
                            null,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));

            assertThatThrownBy(() -> auctionService.placeBid(1L, 10L, new PlaceBidRequest(1030)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BID_AMOUNT_TOO_LOW);
        }

        @Test
        @DisplayName("입찰 금액이 현재가+10 미만인 경우 BID_AMOUNT_TOO_LOW 예외")
        void placeBid_bidAmountBelowFlatThreshold() {
            // currentPrice=100 → 최소 5%: 105 / 최소 +10: 110 → 최소 110
            Auction auction =
                    mockAuction(
                            10L,
                            100,
                            null,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));

            assertThatThrownBy(() -> auctionService.placeBid(1L, 10L, new PlaceBidRequest(106)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BID_AMOUNT_TOO_LOW);
        }

        @Test
        @DisplayName("AP 잔액 부족 시 INSUFFICIENT_AP 예외")
        void placeBid_insufficientAp() {
            User bidder = sampleUser(1L, "입찰자");
            Wallet bidderWallet = sampleWallet(bidder, 500); // availableAp=500 < 1100
            Auction auction =
                    mockAuction(
                            10L,
                            1000,
                            null,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));

            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
            given(userRepository.findById(1L)).willReturn(Optional.of(bidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(bidderWallet));

            assertThatThrownBy(() -> auctionService.placeBid(1L, 10L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        }

        @Test
        @DisplayName("AP 부족 시 AuctionBid 저장하지 않음")
        void placeBid_insufficientAp_doesNotSaveBid() {
            User bidder = sampleUser(1L, "입찰자");
            Wallet bidderWallet = sampleWallet(bidder, 500);
            Auction auction =
                    mockAuction(
                            10L,
                            1000,
                            null,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));

            given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
            given(userRepository.findById(1L)).willReturn(Optional.of(bidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(bidderWallet));

            assertThatThrownBy(() -> auctionService.placeBid(1L, 10L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class);

            then(auctionBidRepository).should(never()).save(any());
        }
    }

    // ─── getMyBids() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyBids()")
    class GetMyBids {

        private AuctionBid mockBid(Long auctionId, User bidder, Integer price, boolean isHighest) {
            Auction auction =
                    mockAuction(
                            auctionId,
                            isHighest ? price : price + 500,
                            isHighest ? bidder : sampleUser(99L, "다른유저"),
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));

            AuctionBid bid = mock(AuctionBid.class);
            given(bid.getAuction()).willReturn(auction);
            given(bid.getBidder()).willReturn(bidder);
            given(bid.getPrice()).willReturn(price);
            lenient().when(bid.getBidAt()).thenReturn(LocalDateTime.now().minusMinutes(10));
            return bid;
        }

        @Test
        @DisplayName("정상 조회 시 MyBidListResponse 반환 — isHighestBidder 포함")
        void getMyBids_success() {
            User user = sampleUser(1L, "입찰자");
            AuctionBid bid = mockBid(10L, user, 2100, false); // 최고 입찰자 아님

            PageRequest pageable = PageRequest.of(0, 20);

            given(auctionBidRepository.findLatestBidPerAuctionByBidder(1L))
                    .willReturn(List.of(bid));

            MyBidListResponse response = auctionService.getMyBids(1L, pageable);

            assertThat(response.totalCount()).isEqualTo(1);
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(1);
            assertThat(response.bids()).hasSize(1);

            MyBidListResponse.MyBidItemDto item = response.bids().get(0);
            assertThat(item.auctionId()).isEqualTo(10L);
            assertThat(item.territoryId()).isEqualTo(5L);
            assertThat(item.myBidAmount()).isEqualTo(2100);
            assertThat(item.isHighestBidder()).isFalse();
            assertThat(item.status()).isEqualTo(AuctionStatus.BIDDING);
        }

        @Test
        @DisplayName("내가 최고 입찰자인 경우 isHighestBidder=true")
        void getMyBids_isHighestBidder() {
            User user = sampleUser(1L, "입찰자");
            AuctionBid bid = mockBid(10L, user, 2100, true); // 최고 입찰자

            PageRequest pageable = PageRequest.of(0, 20);

            given(auctionBidRepository.findLatestBidPerAuctionByBidder(1L))
                    .willReturn(List.of(bid));

            MyBidListResponse response = auctionService.getMyBids(1L, pageable);

            assertThat(response.bids().get(0).isHighestBidder()).isTrue();
        }

        @Test
        @DisplayName("입찰 내역 없으면 totalCount=0, 빈 목록 반환")
        void getMyBids_empty() {
            PageRequest pageable = PageRequest.of(0, 20);
            given(auctionBidRepository.findLatestBidPerAuctionByBidder(1L))
                    .willReturn(Collections.emptyList());

            MyBidListResponse response = auctionService.getMyBids(1L, pageable);

            assertThat(response.totalCount()).isEqualTo(0);
            assertThat(response.bids()).isEmpty();
        }
    }

    // ─── getAuctionBidHistory() ───────────────────────────────────────────────

    @Nested
    @DisplayName("getAuctionBidHistory()")
    class GetAuctionBidHistory {

        @Test
        @DisplayName("시작가 포함 전체 입찰 내역을 시간순(ASC)으로 반환")
        void getAuctionBidHistory_success() {
            // 시작가 레코드: bidder=null
            AuctionBid systemBid = mock(AuctionBid.class);
            given(systemBid.getBidder()).willReturn(null);
            given(systemBid.getPrice()).willReturn(1000);
            given(systemBid.getBidAt()).willReturn(LocalDateTime.of(2026, 4, 27, 12, 0));

            User bidder = sampleUser(2L, "입찰왕");
            AuctionBid userBid = mock(AuctionBid.class);
            given(userBid.getBidder()).willReturn(bidder);
            given(userBid.getPrice()).willReturn(1100);
            given(userBid.getBidAt()).willReturn(LocalDateTime.of(2026, 4, 27, 13, 0));

            Auction auction =
                    mockAuction(
                            1L,
                            1100,
                            bidder,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(auctionBidRepository.findAllByAuctionIdOrderByBidAtAsc(1L))
                    .willReturn(List.of(systemBid, userBid));

            AuctionBidHistoryResponse response = auctionService.getAuctionBidHistory(1L);

            assertThat(response.auctionId()).isEqualTo(1L);
            assertThat(response.bids()).hasSize(2);

            AuctionBidHistoryResponse.BidDto first = response.bids().get(0);
            assertThat(first.price()).isEqualTo(1000);
            assertThat(first.bidderNickname()).isNull(); // 시작가

            AuctionBidHistoryResponse.BidDto second = response.bids().get(1);
            assertThat(second.price()).isEqualTo(1100);
            assertThat(second.bidderNickname()).isEqualTo("입찰왕");
        }

        @Test
        @DisplayName("입찰 내역 없으면 빈 bids 리스트 반환")
        void getAuctionBidHistory_empty() {
            Auction auction =
                    mockAuction(
                            1L,
                            1000,
                            null,
                            LocalDateTime.now().plusHours(1),
                            LocalDateTime.now().plusHours(2));
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(auctionBidRepository.findAllByAuctionIdOrderByBidAtAsc(1L))
                    .willReturn(Collections.emptyList());

            AuctionBidHistoryResponse response = auctionService.getAuctionBidHistory(1L);

            assertThat(response.auctionId()).isEqualTo(1L);
            assertThat(response.bids()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 auctionId 시 AUCTION_NOT_FOUND 예외")
        void getAuctionBidHistory_notFound() {
            given(auctionRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> auctionService.getAuctionBidHistory(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);
        }
    }

    // ─── getTerritoryAuctionHistory() ─────────────────────────────────────────

    @Nested
    @DisplayName("getTerritoryAuctionHistory()")
    class GetTerritoryAuctionHistory {

        private AuctionHistory mockHistory(
                Long auctionId, String winnerNickname, int finalPrice, LocalDateTime wonAt) {
            User winner = sampleUser(auctionId * 10, winnerNickname);

            Auction auction = mock(Auction.class);
            given(auction.getId()).willReturn(auctionId);

            AuctionHistory history = mock(AuctionHistory.class);
            given(history.getAuction()).willReturn(auction);
            given(history.getWinner()).willReturn(winner);
            given(history.getFinalPrice()).willReturn(finalPrice);
            given(history.getWonAt()).willReturn(wonAt);
            return history;
        }

        @Test
        @DisplayName("낙찰 이력을 최신순(DESC)으로 반환")
        void getTerritoryAuctionHistory_success() {
            Territory territory = mock(Territory.class);
            given(territory.getId()).willReturn(5L);
            given(territoryRepository.findById(5L)).willReturn(Optional.of(territory));

            AuctionHistory h1 = mockHistory(3L, "정복왕", 3500, LocalDateTime.of(2026, 4, 20, 18, 0));
            AuctionHistory h2 = mockHistory(1L, "입찰왕", 2000, LocalDateTime.of(2026, 4, 14, 12, 0));

            given(auctionHistoryRepository.findAllByTerritoryIdOrderByWonAtDesc(5L))
                    .willReturn(List.of(h1, h2));

            TerritoryAuctionHistoryResponse response =
                    auctionService.getTerritoryAuctionHistory(5L);

            assertThat(response.territoryId()).isEqualTo(5L);
            assertThat(response.histories()).hasSize(2);

            TerritoryAuctionHistoryResponse.HistoryDto first = response.histories().get(0);
            assertThat(first.auctionId()).isEqualTo(3L);
            assertThat(first.winnerNickname()).isEqualTo("정복왕");
            assertThat(first.finalPrice()).isEqualTo(3500);
            assertThat(first.wonAt()).isEqualTo(LocalDateTime.of(2026, 4, 20, 18, 0));

            TerritoryAuctionHistoryResponse.HistoryDto second = response.histories().get(1);
            assertThat(second.auctionId()).isEqualTo(1L);
            assertThat(second.winnerNickname()).isEqualTo("입찰왕");
        }

        @Test
        @DisplayName("낙찰 이력 없으면 빈 histories 리스트 반환")
        void getTerritoryAuctionHistory_empty() {
            Territory territory = mock(Territory.class);
            given(territory.getId()).willReturn(5L);
            given(territoryRepository.findById(5L)).willReturn(Optional.of(territory));
            given(auctionHistoryRepository.findAllByTerritoryIdOrderByWonAtDesc(5L))
                    .willReturn(Collections.emptyList());

            TerritoryAuctionHistoryResponse response =
                    auctionService.getTerritoryAuctionHistory(5L);

            assertThat(response.territoryId()).isEqualTo(5L);
            assertThat(response.histories()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 territoryId 시 TERRITORY_NOT_FOUND 예외")
        void getTerritoryAuctionHistory_notFound() {
            given(territoryRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> auctionService.getTerritoryAuctionHistory(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
        }
    }
}
