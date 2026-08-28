package com.territorial.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.client.BidEscrowResult;
import com.territorial.auction.client.WalletClient;
import com.territorial.auction.dto.AuctionBidHistoryResponse;
import com.territorial.auction.dto.AuctionDetailResponse;
import com.territorial.auction.dto.AuctionListResponse;
import com.territorial.auction.dto.MyBidListResponse;
import com.territorial.auction.dto.PlaceBidRequest;
import com.territorial.auction.dto.PlaceBidResponse;
import com.territorial.auction.dto.TerritoryAuctionHistoryResponse;
import com.territorial.auction.entity.Auction;
import com.territorial.auction.entity.AuctionBid;
import com.territorial.auction.entity.AuctionHistory;
import com.territorial.auction.entity.AuctionStatus;
import com.territorial.auction.event.EventPublisher;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @InjectMocks private AuctionService auctionService;

    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionBidRepository auctionBidRepository;
    @Mock private AuctionHistoryRepository auctionHistoryRepository;
    @Mock private WalletClient walletClient;
    @Mock private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        // placeBid가 afterCommit 동기화를 등록하므로 활성 동기화가 필요하다.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Auction auction(long id, int currentPrice, LocalDateTime endAt) {
        Auction a =
                Auction.builder()
                        .territoryId(1L)
                        .coordX(5)
                        .coordY(7)
                        .continentName("북부")
                        .continentId(1L)
                        .grade("A")
                        .currentPrice(currentPrice)
                        .startAt(LocalDateTime.now().minusHours(1))
                        .endAt(endAt)
                        .maxExtendUntil(endAt.plusMinutes(30))
                        .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private Auction activeAuction(int currentPrice) {
        return auction(1L, currentPrice, LocalDateTime.now().plusHours(1));
    }

    private AuctionBid bid(Auction a, Long bidderId, String nickname, int price) {
        AuctionBid b =
                AuctionBid.builder()
                        .auction(a)
                        .bidderId(bidderId)
                        .bidderNickname(nickname)
                        .price(price)
                        .build();
        ReflectionTestUtils.setField(b, "bidAt", LocalDateTime.now());
        return b;
    }

    @Nested
    @DisplayName("getAuctions()")
    class GetAuctions {

        @Test
        @DisplayName("필터·페이지네이션 응답 반환")
        void getAuctions_returnsPage() {
            Auction a = activeAuction(1000);
            given(auctionRepository.findAllWithFilter(eq(1L), eq("BIDDING"), any(), any()))
                    .willReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1));

            AuctionListResponse res =
                    auctionService.getAuctions(1L, AuctionStatus.BIDDING, PageRequest.of(0, 20));

            assertThat(res.totalCount()).isEqualTo(1);
            assertThat(res.auctions()).hasSize(1);
            assertThat(res.auctions().get(0).auctionId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("필터 없으면 status=null로 조회")
        void getAuctions_noFilter() {
            given(auctionRepository.findAllWithFilter(any(), eq(null), any(), any()))
                    .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            AuctionListResponse res = auctionService.getAuctions(null, null, PageRequest.of(0, 20));

            assertThat(res.totalCount()).isZero();
            assertThat(res.auctions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAuctionDetail()")
    class GetAuctionDetail {

        @Test
        @DisplayName("정상 조회 — recentBids 포함")
        void getAuctionDetail_withBids() {
            Auction a = activeAuction(2000);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
            given(auctionBidRepository.findTop5ByAuctionIdOrderByBidAtDesc(1L))
                    .willReturn(List.of(bid(a, 3L, "입찰왕", 2000)));

            AuctionDetailResponse res = auctionService.getAuctionDetail(1L);

            assertThat(res.auctionId()).isEqualTo(1L);
            assertThat(res.recentBids()).hasSize(1);
            assertThat(res.recentBids().get(0).bidderNickname()).isEqualTo("입찰왕");
        }

        @Test
        @DisplayName("입찰 없으면 recentBids 빈 리스트")
        void getAuctionDetail_noBids() {
            Auction a = activeAuction(1000);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
            given(auctionBidRepository.findTop5ByAuctionIdOrderByBidAtDesc(1L))
                    .willReturn(List.of());

            AuctionDetailResponse res = auctionService.getAuctionDetail(1L);

            assertThat(res.recentBids()).isEmpty();
        }

        @Test
        @DisplayName("없는 경매 → AUCTION_NOT_FOUND")
        void getAuctionDetail_notFound() {
            given(auctionRepository.findById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> auctionService.getAuctionDetail(9L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("placeBid()")
    class PlaceBid {

        @Test
        @DisplayName("정상 입찰 → 에스크로 호출 + 최고가 갱신 + 응답 반환")
        void placeBid_success() {
            Auction a = activeAuction(1000);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
            given(walletClient.bidEscrow(any())).willReturn(new BidEscrowResult("입찰왕"));

            PlaceBidResponse res = auctionService.placeBid(3L, 1L, new PlaceBidRequest(1100));

            assertThat(res.newPrice()).isEqualTo(1100);
            assertThat(a.getCurrentBidderId()).isEqualTo(3L);
            assertThat(a.getCurrentBidderNickname()).isEqualTo("입찰왕");
            verify(auctionBidRepository).save(any(AuctionBid.class));
        }

        @Test
        @DisplayName("이전 입찰자 정보를 에스크로에 전달")
        void placeBid_passesPreviousBidder() {
            Auction a = activeAuction(1000);
            a.updateBid(2L, "이전왕", 1000);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
            given(walletClient.bidEscrow(any())).willReturn(new BidEscrowResult("새왕"));

            auctionService.placeBid(3L, 1L, new PlaceBidRequest(1100));

            ArgumentCaptor<com.territorial.auction.client.BidEscrowRequest> captor =
                    ArgumentCaptor.forClass(com.territorial.auction.client.BidEscrowRequest.class);
            verify(walletClient).bidEscrow(captor.capture());
            assertThat(captor.getValue().previousBidderId()).isEqualTo(2L);
            assertThat(captor.getValue().previousAmount()).isEqualTo(1000);
        }

        @Test
        @DisplayName("종료 임박 입찰 → anti-sniping 연장")
        void placeBid_antiSnipingExtends() {
            LocalDateTime soon = LocalDateTime.now().plusSeconds(30);
            Auction a = auction(1L, 1000, soon); // maxExtendUntil = soon + 30분
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
            given(walletClient.bidEscrow(any())).willReturn(new BidEscrowResult("입찰왕"));

            PlaceBidResponse res = auctionService.placeBid(3L, 1L, new PlaceBidRequest(1100));

            assertThat(res.endAt()).isAfter(soon); // 연장됨
        }

        @Test
        @DisplayName("anti-sniping 연장이 maxExtendUntil 초과 시 캡")
        void placeBid_antiSnipingCapped() {
            LocalDateTime soon = LocalDateTime.now().plusSeconds(30);
            Auction a = auction(1L, 1000, soon);
            LocalDateTime cap = soon.plusSeconds(10); // 연장(+30s)보다 짧은 상한
            ReflectionTestUtils.setField(a, "maxExtendUntil", cap);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
            given(walletClient.bidEscrow(any())).willReturn(new BidEscrowResult("입찰왕"));

            PlaceBidResponse res = auctionService.placeBid(3L, 1L, new PlaceBidRequest(1100));

            assertThat(res.endAt()).isEqualTo(cap);
        }

        @Test
        @DisplayName("종료된 경매 → AUCTION_ALREADY_ENDED")
        void placeBid_ended() {
            Auction a = auction(1L, 1000, LocalDateTime.now().minusMinutes(1));
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

            assertThatThrownBy(() -> auctionService.placeBid(3L, 1L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_ALREADY_ENDED);
            verify(walletClient, never()).bidEscrow(any());
        }

        @Test
        @DisplayName("이미 최고 입찰자 → ALREADY_HIGHEST_BIDDER")
        void placeBid_alreadyHighest() {
            Auction a = activeAuction(1000);
            a.updateBid(3L, "입찰왕", 1000);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

            assertThatThrownBy(() -> auctionService.placeBid(3L, 1L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ALREADY_HIGHEST_BIDDER);
            verify(walletClient, never()).bidEscrow(any());
        }

        @Test
        @DisplayName("5% 인상 미달 → BID_AMOUNT_TOO_LOW")
        void placeBid_tooLowPercent() {
            Auction a = activeAuction(1000); // 최소 = max(1050, 1010) = 1050
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

            assertThatThrownBy(() -> auctionService.placeBid(3L, 1L, new PlaceBidRequest(1040)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BID_AMOUNT_TOO_LOW);
        }

        @Test
        @DisplayName("현재가+10 미달 → BID_AMOUNT_TOO_LOW")
        void placeBid_tooLowFlat() {
            Auction a = activeAuction(100); // 최소 = max(ceil(105), 110) = 110
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

            assertThatThrownBy(() -> auctionService.placeBid(3L, 1L, new PlaceBidRequest(108)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BID_AMOUNT_TOO_LOW);
        }

        @Test
        @DisplayName("없는 경매 → AUCTION_NOT_FOUND")
        void placeBid_notFound() {
            given(auctionRepository.findById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> auctionService.placeBid(3L, 9L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getMyBids()")
    class GetMyBids {

        @Test
        @DisplayName("내가 최고 입찰자면 isHighestBidder=true")
        void getMyBids_highest() {
            Auction a = activeAuction(2000);
            a.updateBid(3L, "나", 2000);
            given(auctionBidRepository.findLatestBidPerAuctionByBidder(3L))
                    .willReturn(List.of(bid(a, 3L, "나", 2000)));

            MyBidListResponse res = auctionService.getMyBids(3L, Pageable.unpaged());

            assertThat(res.bids()).hasSize(1);
            assertThat(res.bids().get(0).isHighestBidder()).isTrue();
        }

        @Test
        @DisplayName("입찰 없으면 빈 목록")
        void getMyBids_empty() {
            given(auctionBidRepository.findLatestBidPerAuctionByBidder(3L)).willReturn(List.of());

            MyBidListResponse res = auctionService.getMyBids(3L, Pageable.unpaged());

            assertThat(res.totalCount()).isZero();
            assertThat(res.bids()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAuctionBidHistory()")
    class GetAuctionBidHistory {

        @Test
        @DisplayName("전체 입찰 내역 반환")
        void getAuctionBidHistory_success() {
            Auction a = activeAuction(2000);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
            given(auctionBidRepository.findAllByAuctionIdOrderByBidAtAsc(1L))
                    .willReturn(List.of(bid(a, null, null, 1000), bid(a, 3L, "입찰왕", 2000)));

            AuctionBidHistoryResponse res = auctionService.getAuctionBidHistory(1L);

            assertThat(res.bids()).hasSize(2);
        }

        @Test
        @DisplayName("없는 경매 → AUCTION_NOT_FOUND")
        void getAuctionBidHistory_notFound() {
            given(auctionRepository.findById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> auctionService.getAuctionBidHistory(9L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getTerritoryAuctionHistory()")
    class GetTerritoryAuctionHistory {

        @Test
        @DisplayName("낙찰 이력 반환")
        void getTerritoryAuctionHistory_success() {
            Auction a = activeAuction(2000);
            AuctionHistory h =
                    AuctionHistory.builder()
                            .auction(a)
                            .territoryId(1L)
                            .winnerId(3L)
                            .winnerName("낙찰자")
                            .finalPrice(2000)
                            .wonAt(LocalDateTime.now())
                            .build();
            given(auctionHistoryRepository.findAllByTerritoryIdOrderByWonAtDesc(1L))
                    .willReturn(List.of(h));

            TerritoryAuctionHistoryResponse res = auctionService.getTerritoryAuctionHistory(1L);

            assertThat(res.histories()).hasSize(1);
            assertThat(res.histories().get(0).winnerNickname()).isEqualTo("낙찰자");
        }

        @Test
        @DisplayName("이력 없으면 빈 목록")
        void getTerritoryAuctionHistory_empty() {
            given(auctionHistoryRepository.findAllByTerritoryIdOrderByWonAtDesc(1L))
                    .willReturn(List.of());

            TerritoryAuctionHistoryResponse res = auctionService.getTerritoryAuctionHistory(1L);

            assertThat(res.histories()).isEmpty();
        }
    }
}
