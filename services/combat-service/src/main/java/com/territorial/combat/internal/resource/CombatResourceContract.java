package com.territorial.combat.internal.resource;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class CombatResourceContract {

    private CombatResourceContract() {}

    public record UserSummary(long vaultGp, Long islandId, int islandLevel) {}

    public record TerritoryUnitCount(Long territoryId, long unitCount) {}

    public record BuildingView(Long buildingId, String name, int level, int hp, int maxHp) {}

    public record TerritoryStorageView(
            List<BuildingView> buildings, int storedGp, int storageCapacity) {}

    public record CreditGpRequest(
            @NotNull Long userId, @Min(1) int amount, @NotBlank String commandKey) {}

    public record CreditAttackTokensRequest(
            @NotNull Long userId,
            @Min(0) int normalCount,
            @Min(0) int precisionCount,
            @NotBlank String commandKey) {}

    public record ChargeTaxRequest(
            @NotNull Long userId,
            @Min(1) int amount,
            @NotNull List<Long> territoryIds,
            @NotBlank String commandKey) {}

    public record ChargeTaxResponse(boolean paid) {}

    public record CreditIncomeRequest(@Min(0) int amount, @NotBlank String commandKey) {}

    public record CreditIncomeResponse(int creditedGp, int storedGp, int storageCapacity) {}

    public record GpBalanceResponse(int vaultGp) {}

    public record AttackTokenBalanceResponse(int normalCount, int precisionCount) {}
}
