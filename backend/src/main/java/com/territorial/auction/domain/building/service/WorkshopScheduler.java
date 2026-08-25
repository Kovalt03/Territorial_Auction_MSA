package com.territorial.auction.domain.building.service;

import com.territorial.auction.domain.building.BuildingPolicy;
import com.territorial.auction.domain.building.StoragePolicy;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopScheduler {

    private final BuildingInstanceRepository buildingInstanceRepository;
    private final com.territorial.auction.domain.building.repository.HomeIslandRepository
            homeIslandRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void produceWorkshopGp() {
        LocalDateTime now = LocalDateTime.now();
        int credited = 0;
        for (Object[] row :
                buildingInstanceRepository.sumWorkshopGpProductionGroupedByTerritory(now)) {
            Long territoryId = (Long) row[0];
            int amount = ((Number) row[1]).intValue();
            creditGp(
                    buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(
                            territoryId),
                    amount);
            credited++;
        }
        for (Object[] row :
                buildingInstanceRepository.sumWorkshopGpProductionGroupedByIsland(now)) {
            Long islandId = (Long) row[0];
            int amount = ((Number) row[1]).intValue();
            creditGp(
                    buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(islandId),
                    applyIslandBoost(islandId, amount, now));
            credited++;
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

    // GP는 저장소부터 채우고 넘치면 성으로. 저장 공간이 없으면 그 위치 생산분은 버려진다.
    private void creditGp(List<BuildingInstance> storages, int amount) {
        if (storages.isEmpty() || amount <= 0) {
            return;
        }
        StoragePolicy.fillGp(storages, amount);
    }
}
