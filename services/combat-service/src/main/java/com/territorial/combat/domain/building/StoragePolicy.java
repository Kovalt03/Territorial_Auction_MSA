package com.territorial.combat.domain.building;

import com.territorial.combat.domain.building.entity.BuildingInstance;
import java.util.Comparator;
import java.util.List;

/**
 * 한 위치(영토/섬)의 GP·식량 저장 공간을 다룬다.
 *
 * <p>저장 공간은 성(소량·안전)과 저장소(대량·약탈)에 걸쳐 있다. 적립은 저장소부터 채우고 넘치면 성으로, 소진은 반대로 성부터 빼서 저장소에 위험을 몰아준다.
 *
 * <p>서비스 의존 없이 여러 도메인이 공유하도록 정적 헬퍼로 둔다. 호출측은 {@code findStorageBuildingsBy...WithLock} 으로 성·저장소를 함께
 * 가져와 넘긴다.
 */
public final class StoragePolicy {

    private StoragePolicy() {}

    /** 성 레벨당 저장 용량 (GP·식량 각각). 성은 약탈되지 않는다. */
    public static final int CASTLE_CAPACITY_PER_LEVEL = 5_000;

    /** 저장소 레벨당 저장 용량 (GP·식량 각각). 저장소는 약탈 대상이다. */
    public static final int STORAGE_CAPACITY_PER_LEVEL = 5_000;

    /** 영토 상실(성 파괴 인계·토지세 미납·점유 만료) 시 저장 GP를 회수하는 비율. 나머지는 소멸한다. */
    public static final double TERRITORY_LOSS_TRANSFER_RATE = 0.8;

    public static int capacity(BuildingInstance building) {
        int perLevel =
                building.getBuildingType().isCastle()
                        ? CASTLE_CAPACITY_PER_LEVEL
                        : STORAGE_CAPACITY_PER_LEVEL;
        return building.getLevel() * perLevel;
    }

    // JOIN FETCH 는 쿼리 ORDER BY 를 무시할 수 있어 정렬은 여기서 한다.
    private static final Comparator<BuildingInstance> CASTLE_FIRST =
            Comparator.comparingInt(b -> b.getBuildingType().isCastle() ? 0 : 1);
    private static final Comparator<BuildingInstance> STORAGE_FIRST =
            Comparator.comparingInt(b -> b.getBuildingType().isCastle() ? 1 : 0);

    public static int totalGp(List<BuildingInstance> storages) {
        return storages.stream().mapToInt(BuildingInstance::getStoredGp).sum();
    }

    public static int roomGp(List<BuildingInstance> storages) {
        return storages.stream().mapToInt(b -> capacity(b) - b.getStoredGp()).sum();
    }

    /** 저장소부터 채운다. 다 못 넣으면 넘친 양을 돌려준다. */
    public static int fillGp(List<BuildingInstance> storages, int amount) {
        int remaining = amount;
        for (BuildingInstance b : storages.stream().sorted(STORAGE_FIRST).toList()) {
            remaining -= b.fillGp(remaining, capacity(b));
            if (remaining == 0) break;
        }
        return remaining;
    }

    /** 성부터 뺀다. 다 못 빼면 부족한 양을 돌려준다. */
    public static int drainGp(List<BuildingInstance> storages, int amount) {
        int remaining = amount;
        for (BuildingInstance b : storages.stream().sorted(CASTLE_FIRST).toList()) {
            remaining -= b.drainGp(remaining);
            if (remaining == 0) break;
        }
        return remaining;
    }

    /** 모든 저장 공간의 GP를 전부 뺀다(영토 상실 정산용). 뺀 총량을 돌려준다. */
    public static int drainAllGp(List<BuildingInstance> storages) {
        int total = totalGp(storages);
        for (BuildingInstance b : storages) {
            b.drainGp(b.getStoredGp());
        }
        return total;
    }

    public static int totalFood(List<BuildingInstance> storages) {
        return storages.stream().mapToInt(BuildingInstance::getStoredFood).sum();
    }

    /** 모든 저장 공간의 식량을 전부 뺀다(영토 상실 시 소멸). 뺀 총량을 돌려준다. */
    public static int drainAllFood(List<BuildingInstance> storages) {
        int total = totalFood(storages);
        for (BuildingInstance b : storages) {
            b.drainFood(b.getStoredFood());
        }
        return total;
    }

    public static int fillFood(List<BuildingInstance> storages, int amount) {
        int remaining = amount;
        for (BuildingInstance b : storages.stream().sorted(STORAGE_FIRST).toList()) {
            remaining -= b.fillFood(remaining, capacity(b));
            if (remaining == 0) break;
        }
        return remaining;
    }

    public static int drainFood(List<BuildingInstance> storages, int amount) {
        int remaining = amount;
        for (BuildingInstance b : storages.stream().sorted(CASTLE_FIRST).toList()) {
            remaining -= b.drainFood(remaining);
            if (remaining == 0) break;
        }
        return remaining;
    }
}
