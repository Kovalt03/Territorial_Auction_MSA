package com.territorial.auction.service;

import com.territorial.auction.AuctionPolicy;
import com.territorial.auction.client.BuildingClient;
import com.territorial.auction.client.TerritoryClient;
import com.territorial.auction.client.WalletClient;
import com.territorial.auction.entity.Auction;
import com.territorial.auction.entity.AuctionHistory;
import com.territorial.auction.event.AuctionClosedEvent;
import com.territorial.auction.event.AuctionSettledEvent;
import com.territorial.auction.event.EventPublisher;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 정산 핵심만 남김. 옮긴 동작(알림·랭킹·map 브로드캐스트·영토 만료·경매 생성·admin)의
// 추적은 docs/design/msa/auction-migration-tracking.md 참고.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionLifecycleService {

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionHistoryRepository auctionHistoryRepository;
    private final TerritoryClient territoryClient;
    private final WalletClient walletClient;
    private final BuildingClient buildingClient;
    private final EventPublisher eventPublisher;

    /** 종료된 미정산 경매를 일괄 정산 (1분 주기 스케줄러가 호출) */
    @Transactional
    public void settlePendingAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> expired = auctionRepository.findAllExpiredUnsettled(now);
        for (Auction auction : expired) {
            try {
                settleAuction(auction, now);
            } catch (Exception e) {
                log.error("[AuctionLifecycle] 경매 정산 실패 auctionId={}", auction.getId(), e);
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
        settleAuction(auction, LocalDateTime.now());
    }

    /** 관리자 강제 취소: 현재 입찰자 잠금 AP 환불 + 영토 재경매 예약 + 경매 종료. */
    @Transactional
    public void forceCancel(Long auctionId) {
        Auction auction = findUnsettledOrThrow(auctionId);
        LocalDateTime now = LocalDateTime.now();
        if (auction.getCurrentBidderId() != null) {
            walletClient.refundLocked(auction.getCurrentBidderId(), auction.getCurrentPrice());
        }
        territoryClient.release(
                auction.getTerritoryId(), now.plusHours(AuctionPolicy.IDLE_REAUCTION_DELAY_HOURS));
        auction.settle();

        AuctionClosedEvent closedEvent =
                new AuctionClosedEvent(auction.getId(), auction.getTerritoryId());
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publish("auction.closed", closedEvent);
                    }
                });
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

    private void settleAuction(Auction auction, LocalDateTime now) {
        if (auction.getCurrentBidderId() != null) {
            Long winnerId = auction.getCurrentBidderId();
            int finalPrice = auction.getCurrentPrice();
            LocalDateTime occupiedUntil = now.plusDays(AuctionPolicy.OCCUPATION_DURATION_DAYS);
            LocalDateTime protectedUntil = now.plusHours(AuctionPolicy.PROTECTION_DURATION_HOURS);

            // 동기: 소유권·돈·성 — 전부 auction 스냅샷 ID로 호출
            territoryClient.occupy(
                    auction.getTerritoryId(), winnerId, occupiedUntil, protectedUntil);
            walletClient.consumeLocked(winnerId, finalPrice, auction.getId());
            buildingClient.createInitialCastle(auction.getTerritoryId());
            // TODO(보상): consume/castle 실패 시 occupy 되돌리기(release) — tracking §3

            auctionHistoryRepository.save(
                    AuctionHistory.builder()
                            .auction(auction)
                            .territoryId(auction.getTerritoryId())
                            .winnerId(winnerId)
                            .winnerName(auction.getCurrentBidderNickname())
                            .finalPrice(finalPrice)
                            .wonAt(now)
                            .seasonId(null) // 시즌 귀속은 ranking 소비자가 (tracking §1)
                            .build());

            auction.settle();

            List<Long> runnerUpIds =
                    auctionBidRepository.findDistinctBidderIdsExcluding(auction.getId(), winnerId);
            AuctionSettledEvent event =
                    new AuctionSettledEvent(
                            auction.getId(),
                            auction.getTerritoryId(),
                            auction.getCoordX(),
                            auction.getCoordY(),
                            winnerId,
                            auction.getCurrentBidderNickname(),
                            finalPrice,
                            auction.getGrade(),
                            List.copyOf(runnerUpIds));
            // 비동기: 알림·랭킹·map 브로드캐스트는 소비 서비스가 처리 (tracking §1)
            AuctionClosedEvent closedEvent =
                    new AuctionClosedEvent(auction.getId(), auction.getTerritoryId());
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eventPublisher.publish("auction.settled", event);
                            eventPublisher.publish("auction.closed", closedEvent);
                        }
                    });

            log.info(
                    "[AuctionLifecycle] 낙찰 정산 auctionId={} winnerId={} price={}",
                    auction.getId(),
                    winnerId,
                    finalPrice);
        } else {
            // 무낙찰: 일정 시간 후 재경매
            LocalDateTime nextAuctionAt = now.plusHours(AuctionPolicy.IDLE_REAUCTION_DELAY_HOURS);
            territoryClient.release(auction.getTerritoryId(), nextAuctionAt);
            auction.settle();

            // map 읽기 프로젝션에서 '경매중' 제거 (낙찰·무낙찰 공통)
            AuctionClosedEvent closedEvent =
                    new AuctionClosedEvent(auction.getId(), auction.getTerritoryId());
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eventPublisher.publish("auction.closed", closedEvent);
                        }
                    });

            log.info(
                    "[AuctionLifecycle] 무낙찰 정산 auctionId={} nextAuctionAt={}",
                    auction.getId(),
                    nextAuctionAt);
        }
    }
}
