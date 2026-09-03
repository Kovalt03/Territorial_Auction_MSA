package com.territorial.auction.domain.combat.client;

import java.util.List;

public interface CombatResourceClient {

    UserSummary getUserSummary(Long userId);

    List<TerritoryUnitCount> getTerritoryUnitCounts(List<Long> territoryIds);

    TerritoryStorageView getTerritoryStorage(Long territoryId);

    void creditGp(Long userId, int amount, String commandKey);

    AttackTokenBalance creditAttackTokens(
            Long userId, int normalCount, int precisionCount, String commandKey);

    boolean chargeTax(Long userId, int amount, List<Long> territoryIds, String commandKey);

    CreditIncomeResponse creditIncome(Long territoryId, int amount, String commandKey);

    record UserSummary(long vaultGp, Long islandId, int islandLevel) {}

    record TerritoryUnitCount(Long territoryId, long unitCount) {}

    record BuildingView(Long buildingId, String name, int level, int hp, int maxHp) {}

    record TerritoryStorageView(List<BuildingView> buildings, int storedGp, int storageCapacity) {}

    record CreditIncomeResponse(int creditedGp, int storedGp, int storageCapacity) {}

    record AttackTokenBalance(int normalCount, int precisionCount) {}
}
