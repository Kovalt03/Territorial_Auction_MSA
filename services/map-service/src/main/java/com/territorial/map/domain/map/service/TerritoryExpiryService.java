package com.territorial.map.domain.map.service;

import com.territorial.map.client.RankingHoldClient;
import com.territorial.map.client.SeasonQueryClient;
import com.territorial.map.client.SeasonQueryClient.ActiveSeason;
import com.territorial.map.domain.map.dto.MapUpdateBroadcast;
import com.territorial.map.domain.map.entity.Territory;
import com.territorial.map.domain.map.event.TerritoryLostEvent;
import com.territorial.map.domain.map.repository.TerritoryRepository;
import com.territorial.map.realtime.MapRealtimePublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 점유 기간이 만료된 영토를 IDLE로 전환한다(즉시 재경매 예약). 영토 수명주기 로직을 map이 소유한다. 신규 경매 '생성'은 auction-service가
 * territory.auction-ready 이벤트로 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerritoryExpiryService {

    private final TerritoryRepository territoryRepository;
    private final SeasonQueryClient seasonQueryClient;
    private final RankingHoldClient rankingHoldClient;
    private final ApplicationEventPublisher eventPublisher;
    private final MapRealtimePublisher mapRealtimePublisher;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void releaseExpiredTerritories() {
        LocalDateTime now = LocalDateTime.now();
        List<Territory> expired =
                territoryRepository.findAllExpiredOccupied(Territory.TerritoryStatus.OCCUPIED, now);
        Optional<ActiveSeason> seasonOpt = seasonQueryClient.getActiveSeason();
        for (Territory territory : expired) {
            closeRankingHoldAfterCommit(territory, seasonOpt, now);
            if (territory.getOwnerId() != null) {
                eventPublisher.publishEvent(
                        new TerritoryLostEvent(territory.getId(), territory.getOwnerId()));
            }
            territory.release(now); // 점유 만료 즉시 재경매 예약
            broadcastIdleAfterCommit(territory);
            log.info("[TerritoryExpiry] 영토 점유 만료 territoryId={}", territory.getId());
        }
    }

    // 영토 점유 종료를 ranking-service에 위임한다. 커밋 이후 호출해 롤백 시 불일치를 막는다(best-effort).
    private void closeRankingHoldAfterCommit(
            Territory territory, Optional<ActiveSeason> seasonOpt, LocalDateTime now) {
        if (seasonOpt.isEmpty() || territory.getOwnerId() == null) return;
        Long userId = territory.getOwnerId();
        Long seasonId = seasonOpt.get().seasonId();
        long territoryId = territory.getId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // best-effort — 실패해도 점유 만료는 이미 커밋됨. 조용히 삼키지 않고 로그로 드러낸다
                        // (ranking hold가 열린 채 남는 드리프트는 랭킹 집계 배치가 근사 보정).
                        try {
                            rankingHoldClient.closeHold(userId, seasonId, territoryId, now);
                        } catch (RuntimeException e) {
                            log.warn(
                                    "[TerritoryExpiry] ranking hold close 실패 — userId={} seasonId={}"
                                            + " territoryId={}",
                                    userId,
                                    seasonId,
                                    territoryId,
                                    e);
                        }
                    }
                });
    }

    private void broadcastIdleAfterCommit(Territory territory) {
        long territoryId = territory.getId();
        int coordX = territory.getCoordX();
        int coordY = territory.getCoordY();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        mapRealtimePublisher.publish(
                                new MapUpdateBroadcast(
                                        territoryId, coordX, coordY, null, null, "IDLE"));
                    }
                });
    }
}
