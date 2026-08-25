package com.territorial.auction.domain.auction.scheduler;

import com.territorial.auction.domain.auction.service.AuctionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionLifecycleService auctionLifecycleService;

    /** 1분마다 종료된 경매 정산 */
    @Scheduled(fixedDelay = 60_000)
    public void settle() {
        log.debug("[AuctionScheduler] 경매 정산 실행");
        auctionLifecycleService.settlePendingAuctions();
    }

    /** 1분마다 점유 만료 처리 + 신규 경매 생성 */
    @Scheduled(fixedDelay = 60_000)
    public void createAuctions() {
        log.debug("[AuctionScheduler] 점유 만료 + 경매 생성 실행");
        auctionLifecycleService.releaseExpiredTerritories();
        auctionLifecycleService.createPendingAuctions();
    }
}
