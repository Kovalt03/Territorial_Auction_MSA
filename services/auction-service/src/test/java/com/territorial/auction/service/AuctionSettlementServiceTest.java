package com.territorial.auction.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.client.BuildingClient;
import com.territorial.auction.client.TerritoryClient;
import com.territorial.auction.client.WalletClient;
import com.territorial.auction.entity.Auction;
import com.territorial.auction.event.EventPublisher;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
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
class AuctionSettlementServiceTest {

    @InjectMocks private AuctionSettlementService settlementService;

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
    @DisplayName("단건 정산 — 낙찰자 있음: 점유·소비·성·이력·정산")
    void settleOne_winner() {
        Auction a = auction();
        a.updateBid(3L, "낙찰자", 2000);
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));
        given(auctionBidRepository.findDistinctBidderIdsExcluding(1L, 3L)).willReturn(List.of());

        settlementService.settleOne(1L, LocalDateTime.now());

        verify(territoryClient).occupy(eq(1L), eq(3L), any(), any());
        verify(walletClient).consumeLocked(eq(3L), eq(2000), eq(1L));
        verify(buildingClient).createInitialCastle(1L);
        verify(auctionHistoryRepository).save(any());
        Assertions.assertThat(a.isSettled()).isTrue();
    }

    @Test
    @DisplayName("단건 정산 — 무낙찰: 재경매 예약(release), 이력 없음, 정산")
    void settleOne_noWinner() {
        Auction a = auction(); // currentBidderId null
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        settlementService.settleOne(1L, LocalDateTime.now());

        verify(territoryClient).release(eq(1L), any());
        verify(territoryClient, never()).occupy(any(), any(), any(), any());
        verify(auctionHistoryRepository, never()).save(any());
        Assertions.assertThat(a.isSettled()).isTrue();
    }

    @Test
    @DisplayName("단건 정산 — 이미 정산된 경매는 멱등하게 건너뜀 (재시도·재정산 방어)")
    void settleOne_alreadySettled_isIdempotent() {
        Auction a = auction();
        a.updateBid(3L, "낙찰자", 2000);
        a.settle();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        settlementService.settleOne(1L, LocalDateTime.now());

        verify(territoryClient, never()).occupy(any(), any(), any(), any());
        verify(walletClient, never()).consumeLocked(any(), anyInt(), any());
        verify(auctionHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("단건 정산 — 존재하지 않는 경매는 조용히 무시")
    void settleOne_notFound() {
        given(auctionRepository.findById(1L)).willReturn(Optional.empty());

        settlementService.settleOne(1L, LocalDateTime.now());

        verify(territoryClient, never()).occupy(any(), any(), any(), any());
        verify(territoryClient, never()).release(any(), any());
    }

    @Test
    @DisplayName("정산 실패 기록 — settleAttempts 증가")
    void recordFailedAttempt_increments() {
        Auction a = auction();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        settlementService.recordFailedAttempt(1L);

        Assertions.assertThat(a.getSettleAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("정산 실패 기록 — 이미 정산된 경매는 증가시키지 않음")
    void recordFailedAttempt_settled_noop() {
        Auction a = auction();
        a.settle();
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        settlementService.recordFailedAttempt(1L);

        Assertions.assertThat(a.getSettleAttempts()).isZero();
    }
}
