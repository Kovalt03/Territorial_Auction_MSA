package com.territorial.combat.domain.military.scheduler;

import com.territorial.combat.domain.military.entity.SiegeEvent;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort;
import com.territorial.combat.domain.military.repository.SiegeEventRepository;
import com.territorial.combat.domain.military.service.SiegeResolutionService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(SiegeTerritoryPort.class)
@RequiredArgsConstructor
public class SiegeScheduler {

    private final SiegeEventRepository siegeEventRepository;
    private final SiegeResolutionService siegeResolutionService;

    @Scheduled(fixedDelay = 60_000)
    public void resolveExpiredSieges() {
        List<SiegeEvent> pending =
                siegeEventRepository.findPendingToResolve(
                        SiegeEvent.SiegeStatus.PENDING, LocalDateTime.now());
        if (pending.isEmpty()) {
            return;
        }
        log.info("공성전 처리 시작. count={}", pending.size());
        for (SiegeEvent event : pending) {
            try {
                siegeResolutionService.resolveOneSiege(event);
            } catch (Exception exception) {
                log.error("공성전 처리 실패. siegeId={}", event.getId(), exception);
            }
        }
    }
}
