package com.territorial.auction.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.client.BuildingClient;
import com.territorial.auction.client.TerritoryClient;
import com.territorial.auction.client.WalletClient;
import com.territorial.auction.entity.Auction;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleServiceTest {

    @InjectMocks private AuctionLifecycleService lifecycleService;

    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionBidRepository auctionBidRepository;
    @Mock private AuctionHistoryRepository auctionHistoryRepository;
    @Mock private TerritoryClient territoryClient;
    @Mock private WalletClient walletClient;
    @Mock private BuildingClient buildingClient;
    @Mock private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Auction auction() {
        Auction a =
                Auction.builder()
                        .territoryId(1L)
                        .coordX(5)
                        .coordY(7)
                        .continentName("북부")
                        .continentId(1L)
                        .grade("A")
                        .currentPrice(2000)
                        .startAt(LocalDateTime.now().minusHours(2))
                        .endAt(LocalDateTime.now().minusMinutes(1))
                        .maxExtendUntil(LocalDateTime.now())
                        .build();
        ReflectionTestUtils.setField(a, "id", 1L);
        return a;
    }

    @Test
    @DisplayName("정산 — 만료 경매 없으면 아무 작업 안 함")
    void settlePending_empty() {
        given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of());

        lifecycleService.settlePendingAuctions();

        verify(territoryClient, never()).occupy(any(), any(), any(), any());
        verify(territoryClient, never()).release(any(), any());
    }

    @Test
    @DisplayName("정산 — 낙찰자 있음: 점유·소비·성·이력·정산")
    void settlePending_winner() {
        Auction a = auction();
        a.updateBid(3L, "낙찰자", 2000);
        given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(a));
        given(auctionBidRepository.findDistinctBidderIdsExcluding(any(), eq(3L)))
                .willReturn(List.of());

        lifecycleService.settlePendingAuctions();

        verify(territoryClient).occupy(eq(1L), eq(3L), any(), any());
        verify(walletClient).consumeLocked(eq(3L), eq(2000), any());
        verify(buildingClient).createInitialCastle(1L);
        verify(auctionHistoryRepository).save(any());
        org.assertj.core.api.Assertions.assertThat(a.isSettled()).isTrue();
    }

    @Test
    @DisplayName("정산 — 무낙찰: 재경매 예약(release), 이력 없음, 정산")
    void settlePending_noWinner() {
        Auction a = auction(); // currentBidderId null
        given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(a));

        lifecycleService.settlePendingAuctions();

        verify(territoryClient).release(eq(1L), any());
        verify(territoryClient, never()).occupy(any(), any(), any(), any());
        verify(auctionHistoryRepository, never()).save(any());
        org.assertj.core.api.Assertions.assertThat(a.isSettled()).isTrue();
    }

    @Test
    @DisplayName("정산 — 한 건 실패해도 나머지는 정산(예외 격리)")
    void settlePending_isolatesFailure() {
        Auction fail = auction();
        fail.updateBid(3L, "낙찰자", 2000);
        Auction ok = auction(); // 무낙찰
        ReflectionTestUtils.setField(ok, "id", 2L);
        ReflectionTestUtils.setField(ok, "territoryId", 2L);
        given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(fail, ok));
        org.mockito.BDDMockito.willThrow(new RuntimeException("occupy 실패"))
                .given(territoryClient)
                .occupy(eq(1L), any(), any(), any());

        lifecycleService.settlePendingAuctions();

        // 두 번째(무낙찰)는 정상 정산
        verify(territoryClient).release(eq(2L), any());
        org.assertj.core.api.Assertions.assertThat(ok.isSettled()).isTrue();
    }

    @Test
    @DisplayName("강제 낙찰 — 입찰자 없음 → AUCTION_NO_BIDDER_TO_SETTLE")
    void forceSettle_noBidder() {
        Auction a = auction(); // currentBidderId null
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        assertThatThrownBy(() -> lifecycleService.forceSettle(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUCTION_NO_BIDDER_TO_SETTLE);
        verify(territoryClient, never()).occupy(any(), any(), any(), any());
    }

    @Test
    @DisplayName("강제 낙찰 — 입찰자 존재 → 점유·소비·성 생성 호출")
    void forceSettle_success() {
        Auction a = auction();
        a.updateBid(3L, "낙찰자", 2000);
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
        given(auctionBidRepository.findDistinctBidderIdsExcluding(1L, 3L)).willReturn(List.of());

        lifecycleService.forceSettle(1L);

        verify(territoryClient).occupy(eq(1L), eq(3L), any(), any());
        verify(walletClient).consumeLocked(eq(3L), eq(2000), eq(1L));
        verify(buildingClient).createInitialCastle(1L);
        verify(auctionHistoryRepository).save(any());
    }

    @Test
    @DisplayName("강제 취소 — 입찰자 잠금 AP 환불 + 영토 재경매 예약 + 종료")
    void forceCancel_withBidder() {
        Auction a = auction();
        a.updateBid(3L, "입찰자", 2000);
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        lifecycleService.forceCancel(1L);

        verify(walletClient).refundLocked(3L, 2000);
        verify(territoryClient).release(eq(1L), any());
        org.assertj.core.api.Assertions.assertThat(a.isSettled()).isTrue();
    }

    @Test
    @DisplayName("강제 취소 — 이미 정산된 경매 → AUCTION_ALREADY_SETTLED")
    void forceCancel_alreadySettled() {
        Auction a = auction();
        a.settle();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        assertThatThrownBy(() -> lifecycleService.forceCancel(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUCTION_ALREADY_SETTLED);
        verify(walletClient, never()).refundLocked(anyLong(), anyInt());
    }
}
