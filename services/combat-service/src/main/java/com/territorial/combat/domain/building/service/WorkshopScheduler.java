package com.territorial.combat.domain.building.service;

import com.territorial.combat.domain.building.BuildingPolicy;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.service.ProductionCreditService.Resource;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 비트랜잭션 오케스트레이터 — 각 위치 적립은 ProductionCreditService가 독립 트랜잭션으로 처리(락 경합 최소화).
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopScheduler {

    private final BuildingInstanceRepository buildingInstanceRepository;
    private final com.territorial.combat.domain.building.repository.HomeIslandRepository
            homeIslandRepository;
    private final ProductionCreditService productionCreditService;

    @Scheduled(cron = "0 0 * * * *")
    public void produceWorkshopGp() {
        LocalDateTime now = LocalDateTime.now();
        int credited = 0;
        for (Object[] row :
                buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(now)) {
            Long territoryId = (Long) row[0];
            int amount = ((Number) row[1]).intValue();
            try {
                productionCreditService.creditTerritory(territoryId, amount, Resource.GP);
                credited++;
            } catch (Exception e) {
                log.error("생산소 GP 적립 실패 territoryId={}", territoryId, e);
            }
        }
        for (Object[] row :
                buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(now)) {
            Long islandId = (Long) row[0];
            int amount = applyIslandBoost(islandId, ((Number) row[1]).intValue(), now);
            try {
                productionCreditService.creditIsland(islandId, amount, Resource.GP);
                credited++;
            } catch (Exception e) {
                log.error("생산소 GP 적립 실패(섬) islandId={}", islandId, e);
            }
        }
        log.info("생산소 GP 생산 완료. 적립 위치 수={}", credited);
    }

    // 섬 생산 부스터가 활성이면 적립량에 배율을 곱한다.
    private int applyIslandBoost(Long islandId, int amount, LocalDateTime now) {
        return homeIslandRepository
                        .findById(islandId)
                        .filter(island -> island.isProductionBoostActive(now))
                        .isPresent()
                ? amount * BuildingPolicy.PRODUCTION_BOOST_MULTIPLIER
                : amount;
    }
}
