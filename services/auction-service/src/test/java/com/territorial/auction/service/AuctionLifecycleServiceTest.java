package com.territorial.auction.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.client.TerritoryClient;
import com.territorial.auction.client.WalletClient;
import com.territorial.auction.entity.Auction;
import com.territorial.auction.event.EventPublisher;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleServiceTest {

    @InjectMocks private AuctionLifecycleService lifecycleService;

    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionSettlementService settlementService;
    @Mock private TerritoryClient territoryClient;
    @Mock private WalletClient walletClient;
    @Mock private EventPublisher eventPublisher;

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
    @DisplayName("정산 오케스트레이션 — 만료 경매 없으면 아무 작업 안 함")
    void settlePending_empty() {
        given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of());

        lifecycleService.settlePendingAuctions();

        verify(settlementService, never()).settleOne(any(), any());
    }

    @Test
    @DisplayName("정산 오케스트레이션 — 만료 경매마다 단건 정산 위임")
    void settlePending_delegatesPerAuction() {
        Auction first = auction();
        Auction second = auction();
        ReflectionTestUtils.setField(second, "id", 2L);
        given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(first, second));

        lifecycleService.settlePendingAuctions();

        verify(settlementService).settleOne(eq(1L), any());
        verify(settlementService).settleOne(eq(2L), any());
    }

    @Test
    @DisplayName("정산 오케스트레이션 — 한 건이 예외를 던져도 나머지는 정산 (실제 항목별 트랜잭션 격리)")
    void settlePending_isolatesFailure() {
        Auction fail = auction();
        Auction ok = auction();
        ReflectionTestUtils.setField(ok, "id", 2L);
        given(auctionRepository.findAllExpiredUnsettled(any())).willReturn(List.of(fail, ok));
        willThrow(new RuntimeException("정산 실패")).given(settlementService).settleOne(eq(1L), any());

        lifecycleService.settlePendingAuctions();

        // 첫 건이 자기 트랜잭션에서 롤백되어도 둘째 건은 별도 트랜잭션으로 정산된다.
        verify(settlementService).settleOne(eq(2L), any());
    }

    @Test
    @DisplayName("강제 낙찰 — 입찰자 없음 → AUCTION_NO_BIDDER_TO_SETTLE (정산 위임 안 함)")
    void forceSettle_noBidder() {
        Auction a = auction(); // currentBidderId null
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        assertThatThrownBy(() -> lifecycleService.forceSettle(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUCTION_NO_BIDDER_TO_SETTLE);
        verify(settlementService, never()).settleOne(any(), any());
    }

    @Test
    @DisplayName("강제 낙찰 — 입찰자 존재 → 단건 정산 위임")
    void forceSettle_success() {
        Auction a = auction();
        a.updateBid(3L, "낙찰자", 2000);
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        lifecycleService.forceSettle(1L);

        verify(settlementService).settleOne(eq(1L), any());
    }

    @Test
    @DisplayName("강제 취소 — 입찰자 잠금 AP 환불 + 영토 재경매 예약 + 종료")
    void forceCancel_withBidder() {
        Auction a = auction();
        a.updateBid(3L, "입찰자", 2000);
        given(auctionRepository.findById(1L)).willReturn(Optional.of(a));

        lifecycleService.forceCancel(1L);

        verify(walletClient).refundLocked(3L, 2000, 1L);
        verify(territoryClient).release(eq(1L), any());
        Assertions.assertThat(a.isSettled()).isTrue();
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
        verify(walletClient, never()).refundLocked(anyLong(), anyInt(), anyLong());
    }
}
