package com.territorial.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.client.BidEscrowResult;
import com.territorial.auction.client.WalletClient;
import com.territorial.auction.dto.PlaceBidRequest;
import com.territorial.auction.dto.PlaceBidResponse;
import com.territorial.auction.entity.Auction;
import com.territorial.auction.entity.AuctionBid;
import com.territorial.auction.event.EventPublisher;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
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

    private Auction auction(int currentPrice) {
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
                        .endAt(LocalDateTime.now().plusHours(1))
                        .maxExtendUntil(LocalDateTime.now().plusHours(2))
                        .build();
        ReflectionTestUtils.setField(a, "id", 1L);
        return a;
    }

    @Nested
    @DisplayName("placeBid()")
    class PlaceBid {

        @Test
        @DisplayName("정상 입찰 → 에스크로 호출 + 최고가 갱신 + 응답 반환")
        void placeBid_success() {
            Auction a = auction(1000);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
            given(walletClient.bidEscrow(any())).willReturn(new BidEscrowResult("입찰왕"));

            PlaceBidResponse res = auctionService.placeBid(3L, 1L, new PlaceBidRequest(1100));

            assertThat(res.newPrice()).isEqualTo(1100);
            assertThat(a.getCurrentBidderId()).isEqualTo(3L);
            assertThat(a.getCurrentBidderNickname()).isEqualTo("입찰왕");
            verify(auctionBidRepository).save(any(AuctionBid.class));
        }

        @Test
        @DisplayName("종료된 경매 → AUCTION_ALREADY_ENDED")
        void placeBid_ended() {
            Auction a = auction(1000);
            ReflectionTestUtils.setField(a, "endAt", LocalDateTime.now().minusMinutes(1));
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
            Auction a = auction(1000);
            a.updateBid(3L, "입찰왕", 1000);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

            assertThatThrownBy(() -> auctionService.placeBid(3L, 1L, new PlaceBidRequest(1100)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ALREADY_HIGHEST_BIDDER);
            verify(walletClient, never()).bidEscrow(any());
        }

        @Test
        @DisplayName("최소 인상액 미달 → BID_AMOUNT_TOO_LOW")
        void placeBid_tooLow() {
            Auction a = auction(1000); // 최소 = max(ceil(1000*1.05)=1050, 1000+10=1010) = 1050
            given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

            assertThatThrownBy(() -> auctionService.placeBid(3L, 1L, new PlaceBidRequest(1040)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BID_AMOUNT_TOO_LOW);
            verify(walletClient, never()).bidEscrow(any());
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
}
