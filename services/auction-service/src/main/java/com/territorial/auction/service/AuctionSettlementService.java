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
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매 단건 정산 단위. 각 경매를 <b>독립 트랜잭션</b>으로 정산해, 한 경매의 실패가 다른 경매의 로컬 상태를 롤백시키지 않게 한다. 배치 오케스트레이션은 {@link
 * AuctionLifecycleService}가, 각 건의 원자적 정산은 여기가 담당한다(SiegeResolutionService와 동일 패턴 — 스케줄러 루프는 비트랜잭션,
 * 항목 처리 메서드가 자기 트랜잭션을 연다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionSettlementService {

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionHistoryRepository auctionHistoryRepository;
    private final TerritoryClient territoryClient;
    private final WalletClient walletClient;
    private final BuildingClient buildingClient;
    private final EventPublisher eventPublisher;

    /** 단건 정산. 이미 정산됐거나 사라진 경매는 조용히 건너뛴다(재시도·동시 실행에 멱등). */
    @Transactional
    public void settleOne(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null || auction.isSettled()) {
            return;
        }
        settleAuction(auction, now);
    }

    /**
     * 정산 실패 1회 기록(오케스트레이터가 settleOne 실패 시 호출). 실패한 정산 트랜잭션과 별개의 트랜잭션이라 롤백돼도 카운트는 남는다. 상한 도달 시
     * findRetriableExpired에서 제외되어 무한 재시도가 멈추고, 수동 확인 대상으로 종료된다.
     */
    @Transactional
    public void recordFailedAttempt(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null || auction.isSettled()) {
            return;
        }
        int attempts = auction.recordFailedSettleAttempt();
        if (attempts >= AuctionPolicy.MAX_SETTLE_ATTEMPTS) {
            log.error(
                    "[AuctionSettlement] 정산 영구 실패 — 재시도 상한({}) 도달, 수동 확인 필요."
                            + " auctionId={} territoryId={}",
                    AuctionPolicy.MAX_SETTLE_ATTEMPTS,
                    auction.getId(),
                    auction.getTerritoryId());
        }
    }

    /**
     * 영구 실패 정산 보상. 재시도 상한 도달 후에도 정산이 안 된 경매를 되돌린다: 낙찰자 잠금 AP 환불 + 영토 해제(재경매 예약) + 정산 종료. 정산에서
     * 돈(consume)이 마지막이라 이 시점 AP는 항상 잠금 상태 → refundLocked가 결정적. 모든 원격 호출이 멱등이라 이 보상은 성공할 때까지 안전하게
     * 재시도된다.
     */
    @Transactional
    public void compensateFailedSettlement(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null || auction.isSettled()) {
            return;
        }
        if (auction.getCurrentBidderId() != null) {
            walletClient.refundLocked(
                    auction.getCurrentBidderId(), auction.getCurrentPrice(), auction.getId());
        }
        territoryClient.release(
                auction.getTerritoryId(), now.plusHours(AuctionPolicy.IDLE_REAUCTION_DELAY_HOURS));
        auction.settle();

        eventPublisher.publish(
                "auction.closed",
                new AuctionClosedEvent(auction.getId(), auction.getTerritoryId()));
        log.warn(
                "[AuctionSettlement] 영구 실패 경매 보상 완료(환불+재경매 예약) auctionId={} territoryId={}",
                auction.getId(),
                auction.getTerritoryId());
    }

    private void settleAuction(Auction auction, LocalDateTime now) {
        if (auction.getCurrentBidderId() != null) {
            Long winnerId = auction.getCurrentBidderId();
            int finalPrice = auction.getCurrentPrice();
            LocalDateTime occupiedUntil = now.plusDays(AuctionPolicy.OCCUPATION_DURATION_DAYS);
            LocalDateTime protectedUntil = now.plusHours(AuctionPolicy.PROTECTION_DURATION_HOURS);

            // 동기 사가 — 전부 멱등(occupy: 동일 소유자 skip, castle: 존재 skip, consume: commandKey).
            // 돈(consume)을 마지막에 둔다: 일시 실패는 재시도로 자가치유되고, 영구 실패(상한 도달) 시엔
            // consume이 아직 실행 전이라 AP가 잠금 상태 → 보상은 refundLocked로 결정적이다(compensateFailedSettlement).
            territoryClient.occupy(
                    auction.getTerritoryId(), winnerId, occupiedUntil, protectedUntil);
            buildingClient.createInitialCastle(auction.getTerritoryId());
            walletClient.consumeLocked(winnerId, finalPrice, auction.getId());

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
            eventPublisher.publish("auction.settled", event);
            eventPublisher.publish("auction.closed", closedEvent);

            log.info(
                    "[AuctionSettlement] 낙찰 정산 auctionId={} winnerId={} price={}",
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
            eventPublisher.publish("auction.closed", closedEvent);

            log.info(
                    "[AuctionSettlement] 무낙찰 정산 auctionId={} nextAuctionAt={}",
                    auction.getId(),
                    nextAuctionAt);
        }
    }
}
