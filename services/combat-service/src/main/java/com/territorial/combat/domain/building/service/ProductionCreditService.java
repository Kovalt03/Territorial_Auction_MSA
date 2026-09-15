package com.territorial.combat.domain.building.service;

import com.territorial.combat.domain.building.StoragePolicy;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생산 배치의 위치 단위 자원 적립. 위치(영토/섬)별 <b>독립 트랜잭션</b>으로 저장소 락을 짧게 잡고 즉시 해제한다 — 시간당 배치가 전체 저장소 락을 한 트랜잭션에
 * 오래 붙들어 플레이어 조작(건설·이전 등)과 경합하던 것을 없앤다. 위치별로 독립이라 한 위치 실패가 다른 위치를 롤백시키지도 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProductionCreditService {

    public enum Resource {
        FOOD,
        GP
    }

    private final BuildingInstanceRepository buildingInstanceRepository;

    @Transactional
    public void creditTerritory(Long territoryId, int amount, Resource resource) {
        fill(
                buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(territoryId),
                amount,
                resource);
    }

    @Transactional
    public void creditIsland(Long islandId, int amount, Resource resource) {
        fill(
                buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(islandId),
                amount,
                resource);
    }

    // 저장소부터 채우고 넘치면 성으로. 저장 공간이 없으면 그 위치 생산분은 버려진다.
    private void fill(List<BuildingInstance> storages, int amount, Resource resource) {
        if (storages.isEmpty() || amount <= 0) {
            return;
        }
        if (resource == Resource.FOOD) {
            StoragePolicy.fillFood(storages, amount);
        } else {
            StoragePolicy.fillGp(storages, amount);
        }
    }
}
