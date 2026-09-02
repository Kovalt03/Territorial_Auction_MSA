package com.territorial.combat.domain.building.dto;

import com.territorial.combat.domain.building.BuildingPolicy;
import com.territorial.combat.domain.building.StoragePolicy;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.HomeIsland;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.ToIntFunction;

public record IslandResponse(
        Long islandId,
        String grade,
        int gridSize,
        int level,
        int productionRatePerHour,
        LocalDateTime lastHarvestAt,
        int accumulatedGp,
        int storedGp,
        int storedFood,
        int storageCapacity,
        int zone1Radius,
        int zone2Radius,
        int builderCount,
        int buildersInUse,
        LocalDateTime productionBoostUntil,
        List<IslandBuildingInfo> buildings) {

    public record IslandBuildingInfo(
            Long buildingId,
            String type,
            int posX,
            int posY,
            int hp,
            int maxHp,
            int level,
            int width,
            int height,
            boolean isDestroyed,
            LocalDateTime buildCompleteAt) {

        public static IslandBuildingInfo from(BuildingInstance bi, int maxHp) {
            return new IslandBuildingInfo(
                    bi.getId(),
                    bi.getBuildingType().getName(),
                    bi.getPosX(),
                    bi.getPosY(),
                    bi.getHp(),
                    maxHp,
                    bi.getLevel(),
                    bi.getBuildingType().getWidth(),
                    bi.getBuildingType().getHeight(),
                    bi.isDestroyed(),
                    bi.getBuildCompleteAt());
        }
    }

    public static IslandResponse of(
            HomeIsland island,
            List<BuildingInstance> buildings,
            ToIntFunction<BuildingInstance> gpPerHourFn,
            ToIntFunction<BuildingInstance> maxHpFn,
            int builderCount) {
        List<IslandBuildingInfo> buildingInfos =
                buildings.stream()
                        .map(b -> IslandBuildingInfo.from(b, maxHpFn.applyAsInt(b)))
                        .toList();

        LocalDateTime now = LocalDateTime.now();
        int buildersInUse =
                (int) buildings.stream().filter(b -> b.isUnderConstruction(now)).count();
        int productionRatePerHour =
                buildings.stream()
                        .filter(b -> !b.isDestroyed() && !b.isUnderConstruction(now))
                        .mapToInt(gpPerHourFn)
                        .sum();

        LocalDateTime lastHarvestAt =
                island.getLastHarvestAt() != null
                        ? island.getLastHarvestAt()
                        : island.getCreatedAt() != null
                                ? island.getCreatedAt()
                                : LocalDateTime.now();

        // 24시간 초과분은 버리고, 부스터 구간과 겹친 만큼 배율 가중.
        LocalDateTime from =
                lastHarvestAt.isAfter(
                                now.minusMinutes(BuildingPolicy.MAX_HARVEST_ACCUMULATION_MINUTES))
                        ? lastHarvestAt
                        : now.minusMinutes(BuildingPolicy.MAX_HARVEST_ACCUMULATION_MINUTES);
        long minutesElapsed =
                BuildingPolicy.boostWeightedMinutes(from, now, island.getProductionBoostUntil());

        // 분당으로 먼저 나누면 시간당 생산량이 60 미만인 건물은 0이 되어 버린다.
        int accumulatedGp = (int) (minutesElapsed * productionRatePerHour / 60);

        // 섬 저장소(성·저장소)에 실제 보관된 GP·식량 — 건물 건설·유닛 생산에 차감되는 값.
        List<BuildingInstance> storages =
                buildings.stream()
                        .filter(
                                b ->
                                        (b.getBuildingType().isCastle()
                                                        || "STORAGE"
                                                                .equals(
                                                                        b.getBuildingType()
                                                                                .getName()))
                                                && b.getPosX() >= 0)
                        .toList();
        int storedGp = StoragePolicy.totalGp(storages);
        int storedFood = StoragePolicy.totalFood(storages);
        int storageCapacity = storages.stream().mapToInt(StoragePolicy::capacity).sum();

        return new IslandResponse(
                island.getId(),
                island.getGrade(),
                island.getGridSize(),
                island.getLevel(),
                productionRatePerHour,
                lastHarvestAt,
                accumulatedGp,
                storedGp,
                storedFood,
                storageCapacity,
                island.getZone1Radius(),
                island.getZone2Radius(),
                builderCount,
                buildersInUse,
                island.getProductionBoostUntil(),
                buildingInfos);
    }
}
