package com.territorial.auction.scheduler;

import com.territorial.auction.service.AuctionLifecycleService;
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

    // 점유 만료(map 소유)·경매 생성(#3 이벤트 기반 재설계)은 제거함 — tracking §4 참고.
}
