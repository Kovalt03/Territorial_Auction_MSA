package com.territorial.auction.service;

import com.territorial.auction.AuctionPolicy;
import com.territorial.auction.client.TerritoryClient;
import com.territorial.auction.client.WalletClient;
import com.territorial.auction.entity.Auction;
import com.territorial.auction.event.AuctionClosedEvent;
import com.territorial.auction.event.EventPublisher;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 정산 오케스트레이션. 각 건의 원자적 정산은 AuctionSettlementService가 독립 트랜잭션으로 수행한다.
// 옮긴 동작(알림·랭킹·map 브로드캐스트·영토 만료·경매 생성·admin)의 추적은
// docs/design/msa/auction-migration-tracking.md 참고.
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionLifecycleService {

    private final AuctionRepository auctionRepository;
    private final AuctionSettlementService settlementService;
    private final TerritoryClient territoryClient;
    private final WalletClient walletClient;
    private final EventPublisher eventPublisher;

    /**
     * 종료된 미정산 경매를 일괄 정산 (1분 주기 스케줄러가 호출). 비트랜잭션 — 각 건은 {@link AuctionSettlementService#settleOne}이
     * 자기 트랜잭션에서 처리하므로, 한 건의 실패가 다른 건을 롤백시키지 않는다.
     */
    public void settlePendingAuctions() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction :
                auctionRepository.findRetriableExpired(now, AuctionPolicy.MAX_SETTLE_ATTEMPTS)) {
            try {
                settlementService.settleOne(auction.getId(), now);
            } catch (Exception e) {
                log.error("[AuctionLifecycle] 경매 정산 실패 auctionId={}", auction.getId(), e);
                // 실패는 별도 트랜잭션으로 기록(정산 롤백과 무관). 상한 도달 시 재시도 루프에서 제외된다.
                settlementService.recordFailedAttempt(auction.getId());
            }
        }
    }

    /**
     * 재시도 상한 도달 후에도 미정산인 경매를 보상(되돌림). 비트랜잭션 — 각 건은 {@link
     * AuctionSettlementService#compensateFailedSettlement}이 자기 트랜잭션에서 처리한다. 보상(환불·해제)이 멱등이라 실패해도 다음
     * 주기에 다시 시도되고, 성공하면 정산 종료되어 루프에서 빠진다.
     */
    public void compensateAbandonedAuctions() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction :
                auctionRepository.findAbandonedSettlements(
                        now, AuctionPolicy.MAX_SETTLE_ATTEMPTS)) {
            try {
                settlementService.compensateFailedSettlement(auction.getId(), now);
            } catch (Exception e) {
                log.error(
                        "[AuctionLifecycle] 정산 보상 실패, 다음 주기 재시도 auctionId={}", auction.getId(), e);
            }
        }
    }

    /** 관리자 강제 낙찰: 현재 최고 입찰자에게 즉시 낙찰(정산 로직 재사용). 입찰자 없으면 거부. */
    @Transactional
    public void forceSettle(Long auctionId) {
        Auction auction = findUnsettledOrThrow(auctionId);
        if (auction.getCurrentBidderId() == null) {
            throw new CustomException(ErrorCode.AUCTION_NO_BIDDER_TO_SETTLE);
        }
        settlementService.settleOne(auctionId, LocalDateTime.now());
    }

    /** 관리자 강제 취소: 현재 입찰자 잠금 AP 환불 + 영토 재경매 예약 + 경매 종료. */
    @Transactional
    public void forceCancel(Long auctionId) {
        Auction auction = findUnsettledOrThrow(auctionId);
        LocalDateTime now = LocalDateTime.now();
        if (auction.getCurrentBidderId() != null) {
            walletClient.refundLocked(
                    auction.getCurrentBidderId(), auction.getCurrentPrice(), auction.getId());
        }
        territoryClient.release(
                auction.getTerritoryId(), now.plusHours(AuctionPolicy.IDLE_REAUCTION_DELAY_HOURS));
        auction.settle();

        AuctionClosedEvent closedEvent =
                new AuctionClosedEvent(auction.getId(), auction.getTerritoryId());
        eventPublisher.publish("auction.closed", closedEvent);
        log.info("[AuctionLifecycle] 관리자 강제 취소 auctionId={}", auctionId);
    }

    private Auction findUnsettledOrThrow(Long auctionId) {
        Auction auction =
                auctionRepository
                        .findById(auctionId)
                        .orElseThrow(() -> new CustomException(ErrorCode.AUCTION_NOT_FOUND));
        if (auction.isSettled()) {
            throw new CustomException(ErrorCode.AUCTION_ALREADY_SETTLED);
        }
        return auction;
    }
}
