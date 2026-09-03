package com.territorial.combat.internal.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.BuildingLevelSpecResolver;
import com.territorial.combat.domain.building.StoragePolicy;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.entity.HomeIsland;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingLevelSpecRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.military.entity.AttackToken;
import com.territorial.combat.domain.military.repository.AttackTokenRepository;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
import com.territorial.combat.global.exception.ErrorCode;
import com.territorial.combat.internal.admin.CombatCommand;
import com.territorial.combat.internal.admin.CombatCommandRepository;
import com.territorial.combat.internal.resource.CombatResourceContract.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CombatResourceService {

    private static final String CREDIT_GP = "CREDIT_GP";
    private static final String CREDIT_ATTACK_TOKENS = "CREDIT_ATTACK_TOKENS";
    private static final String CHARGE_TAX = "CHARGE_TAX";
    private static final String CREDIT_INCOME = "CREDIT_INCOME";

    private final GlobalVaultRepository globalVaultRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingLevelSpecRepository buildingLevelSpecRepository;
    private final AttackTokenRepository attackTokenRepository;
    private final UnitInstanceRepository unitInstanceRepository;
    private final CombatCommandRepository commandRepository;
    private final ObjectMapper objectMapper;

    public UserSummary getUserSummary(Long userId) {
        HomeIsland island = homeIslandRepository.findByUserId(userId).orElse(null);
        long vaultGp =
                globalVaultRepository.findById(userId).map(GlobalVault::getStoredGp).orElse(0);
        return new UserSummary(
                vaultGp,
                island != null ? island.getId() : null,
                island != null ? island.getLevel() : 1);
    }

    public List<TerritoryUnitCount> getTerritoryUnitCounts(List<Long> territoryIds) {
        if (territoryIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        unitInstanceRepository
                .sumQuantityGroupByTerritoryIds(territoryIds)
                .forEach(row -> counts.put((Long) row[0], ((Number) row[1]).longValue()));
        return territoryIds.stream()
                .map(id -> new TerritoryUnitCount(id, counts.getOrDefault(id, 0L)))
                .toList();
    }

    public TerritoryStorageView getTerritoryStorage(Long territoryId) {
        List<BuildingInstance> buildings =
                buildingInstanceRepository.findByTerritoryId(territoryId);
        BuildingLevelSpecResolver resolver =
                BuildingLevelSpecResolver.of(buildings, buildingLevelSpecRepository);
        List<BuildingInstance> storages =
                buildingInstanceRepository.findStorageBuildingsByTerritoryId(territoryId);
        return new TerritoryStorageView(
                buildings.stream()
                        .map(
                                building ->
                                        new BuildingView(
                                                building.getId(),
                                                building.getBuildingType().getName(),
                                                building.getLevel(),
                                                building.getHp(),
                                                resolver.maxHp(building)))
                        .toList(),
                StoragePolicy.totalGp(storages),
                storages.stream().mapToInt(StoragePolicy::capacity).sum());
    }

    @Transactional
    public GpBalanceResponse creditGp(CreditGpRequest request) {
        return executeOnce(
                request.commandKey(),
                CREDIT_GP,
                request.userId() + ":" + request.amount(),
                () -> {
                    GlobalVault vault = lockedOrCreateVault(request.userId());
                    vault.receiveGp(request.amount());
                    return new GpBalanceResponse(vault.getStoredGp());
                },
                GpBalanceResponse.class);
    }

    @Transactional
    public AttackTokenBalanceResponse creditAttackTokens(CreditAttackTokensRequest request) {
        return executeOnce(
                request.commandKey(),
                CREDIT_ATTACK_TOKENS,
                request.userId() + ":" + request.normalCount() + ":" + request.precisionCount(),
                () -> {
                    AttackToken token =
                            attackTokenRepository
                                    .findByUserIdWithLock(request.userId())
                                    .orElseGet(
                                            () ->
                                                    attackTokenRepository.save(
                                                            AttackToken.builder()
                                                                    .userId(request.userId())
                                                                    .build()));
                    token.addNormal(request.normalCount());
                    token.addPrecision(request.precisionCount());
                    return new AttackTokenBalanceResponse(
                            token.getNormalCount(), token.getPrecisionCount());
                },
                AttackTokenBalanceResponse.class);
    }

    @Transactional
    public ChargeTaxResponse chargeTax(ChargeTaxRequest request) {
        String fingerprint =
                request.userId()
                        + ":"
                        + request.amount()
                        + ":"
                        + request.territoryIds().stream().distinct().sorted().toList();
        return executeOnce(
                request.commandKey(),
                CHARGE_TAX,
                fingerprint,
                () -> chargeTaxNow(request),
                ChargeTaxResponse.class);
    }

    @Transactional
    public CreditIncomeResponse creditIncome(Long territoryId, CreditIncomeRequest request) {
        return executeOnce(
                request.commandKey(),
                CREDIT_INCOME,
                territoryId + ":" + request.amount(),
                () -> creditIncomeNow(territoryId, request.amount()),
                CreditIncomeResponse.class);
    }

    private ChargeTaxResponse chargeTaxNow(ChargeTaxRequest request) {
        GlobalVault vault = globalVaultRepository.findByIdWithLock(request.userId()).orElse(null);
        List<BuildingInstance> storages = new ArrayList<>();
        request.territoryIds().stream()
                .distinct()
                .sorted()
                .forEach(
                        territoryId ->
                                storages.addAll(
                                        buildingInstanceRepository
                                                .findStorageBuildingsByTerritoryIdWithLock(
                                                        territoryId)));
        int available = (vault != null ? vault.getStoredGp() : 0) + StoragePolicy.totalGp(storages);
        if (available < request.amount()) {
            return new ChargeTaxResponse(false);
        }
        int remaining = request.amount();
        if (vault != null) {
            int fromVault = Math.min(remaining, vault.getStoredGp());
            vault.withdrawGp(fromVault);
            remaining -= fromVault;
        }
        if (remaining > 0) {
            StoragePolicy.drainGp(storages, remaining);
        }
        return new ChargeTaxResponse(true);
    }

    private CreditIncomeResponse creditIncomeNow(Long territoryId, int amount) {
        List<BuildingInstance> storages =
                buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(territoryId);
        if (storages.isEmpty()) {
            throw new CustomException(ErrorCode.STORAGE_NOT_FOUND);
        }
        int overflow = StoragePolicy.fillGp(storages, amount);
        return new CreditIncomeResponse(
                amount - overflow,
                StoragePolicy.totalGp(storages),
                storages.stream().mapToInt(StoragePolicy::capacity).sum());
    }

    private GlobalVault lockedOrCreateVault(Long userId) {
        return globalVaultRepository
                .findByIdWithLock(userId)
                .orElseGet(
                        () ->
                                globalVaultRepository.save(
                                        GlobalVault.builder().userId(userId).build()));
    }

    private <T> T executeOnce(
            String commandKey,
            String commandType,
            String fingerprint,
            Supplier<T> action,
            Class<T> responseType) {
        CombatCommand existing = commandRepository.findById(commandKey).orElse(null);
        if (existing != null) {
            validateCommand(existing, commandType, fingerprint);
            return readResponse(existing, responseType);
        }
        T response = action.get();
        CombatCommand command = new CombatCommand(commandKey, commandType, fingerprint);
        command.recordResponse(writeResponse(response));
        commandRepository.save(command);
        return response;
    }

    private void validateCommand(CombatCommand command, String commandType, String fingerprint) {
        if (!commandType.equals(command.getCommandType())
                || !fingerprint.equals(command.getRequestFingerprint())) {
            throw new CustomException(ErrorCode.WALLET_COMMAND_CONFLICT);
        }
    }

    private String writeResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "combat command response serialization failed", exception);
        }
    }

    private <T> T readResponse(CombatCommand command, Class<T> responseType) {
        try {
            return objectMapper.readValue(command.getResponsePayload(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "combat command response deserialization failed", exception);
        }
    }
}
