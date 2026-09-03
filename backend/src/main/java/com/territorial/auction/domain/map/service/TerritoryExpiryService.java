package com.territorial.auction.domain.map.service;

import com.territorial.auction.domain.map.dto.MapUpdateBroadcast;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.event.TerritoryLostEvent;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.ranking.event.TerritoryHoldClosedEvent;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 점유 기간이 만료된 영토를 IDLE로 전환한다(즉시 재경매 예약). auction 도메인 분리 전 AuctionLifecycleService에 있던 영토 수명주기 로직을
 * map이 소유한다. 신규 경매 '생성'은 auction-service가 territory.auction-ready 이벤트로 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerritoryExpiryService {

    private final TerritoryRepository territoryRepository;
    private final SeasonRepository seasonRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void releaseExpiredTerritories() {
        LocalDateTime now = LocalDateTime.now();
        List<Territory> expired =
                territoryRepository.findAllExpiredOccupied(Territory.TerritoryStatus.OCCUPIED, now);
        Optional<Season> seasonOpt = seasonRepository.findActiveSeason(now);
        for (Territory territory : expired) {
            publishHoldClosedEvent(territory, seasonOpt, now);
            if (territory.getOwner() != null) {
                eventPublisher.publishEvent(
                        new TerritoryLostEvent(territory.getId(), territory.getOwner().getId()));
            }
            territory.release(now); // 점유 만료 즉시 재경매 예약
            broadcastIdleAfterCommit(territory);
            log.info("[TerritoryExpiry] 영토 점유 만료 territoryId={}", territory.getId());
        }
    }

    private void publishHoldClosedEvent(
            Territory territory, Optional<Season> seasonOpt, LocalDateTime now) {
        if (seasonOpt.isEmpty() || territory.getOwner() == null) return;
        eventPublisher.publishEvent(
                new TerritoryHoldClosedEvent(
                        territory.getOwner().getId(),
                        seasonOpt.get().getId(),
                        territory.getId(),
                        now));
    }

    private void broadcastIdleAfterCommit(Territory territory) {
        long territoryId = territory.getId();
        int coordX = territory.getCoordX();
        int coordY = territory.getCoordY();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(
                                "/sub/map/update",
                                new MapUpdateBroadcast(
                                        territoryId, coordX, coordY, null, null, "IDLE"));
                    }
                });
    }
}
