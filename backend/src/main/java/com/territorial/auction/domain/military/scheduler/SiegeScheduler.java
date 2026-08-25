package com.territorial.auction.domain.military.scheduler;

import com.territorial.auction.domain.military.entity.SiegeEvent;
import com.territorial.auction.domain.military.repository.SiegeEventRepository;
import com.territorial.auction.domain.military.service.SiegeService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SiegeScheduler {

    private final SiegeEventRepository siegeEventRepository;
    private final SiegeService siegeService;

    @Scheduled(fixedDelay = 60_000)
    public void resolveExpiredSieges() {
        List<SiegeEvent> pending =
                siegeEventRepository.findPendingToResolve(
                        SiegeEvent.SiegeStatus.PENDING, LocalDateTime.now());
        if (pending.isEmpty()) return;
        log.info("공성전 처리 시작. count={}", pending.size());
        for (SiegeEvent event : pending) {
            try {
                siegeService.resolveOneSiege(event);
            } catch (Exception e) {
                log.error("공성전 처리 실패. siegeId={}", event.getId(), e);
            }
        }
    }
}
