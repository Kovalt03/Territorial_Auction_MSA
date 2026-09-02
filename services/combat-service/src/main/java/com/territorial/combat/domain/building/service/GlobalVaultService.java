package com.territorial.combat.domain.building.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.StoragePolicy;
import com.territorial.combat.domain.building.dto.GlobalVaultResponse;
import com.territorial.combat.domain.building.dto.VaultTransferRequest;
import com.territorial.combat.domain.building.dto.VaultTransferResponse;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.event.TerritoryLostEvent;
import com.territorial.combat.domain.building.port.TerritoryContextPort;
import com.territorial.combat.domain.building.port.TerritoryContextPort.TerritoryContext;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnBean(TerritoryContextPort.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalVaultService {

    private static final int TRANSFER_COOLDOWN_MINUTES = 10;

    private final GlobalVaultRepository globalVaultRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final CombatUserSnapshotRepository userSnapshotRepository;
    private final TerritoryContextPort territoryContextPort;

    @Transactional
    public GlobalVaultResponse getVault(Long userId) {
        GlobalVault vault = findOrCreateVault(userId);
        LocalDateTime next = nextTransferAvailableAt(vault);
        boolean isAvailable = next == null || !LocalDateTime.now().isBefore(next);
        return new GlobalVaultResponse(
                vault.getStoredGp(),
                vault.getCapacity(),
                vault.getLastTransferAt(),
                next,
                isAvailable);
    }

    @Transactional
    public VaultTransferResponse transfer(Long userId, VaultTransferRequest request) {
        TerritoryContext territory = findTerritory(request.sourceTerritoryId());
        validateTerritoryOwner(territory, userId);
        GlobalVault vault = findOrCreateVault(userId);
        validateCooldown(vault);
        List<BuildingInstance> storages = findTerritoryStorages(territory.territoryId());
        return "TO_VAULT".equals(request.direction())
                ? transferToVault(vault, storages, request)
                : transferFromVault(vault, storages, request);
    }

    @Transactional
    public void handleTerritoryLost(TerritoryLostEvent event) {
        List<BuildingInstance> storages =
                buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(
                        event.territoryId());
        if (storages.isEmpty()) {
            return;
        }
        int totalGp = StoragePolicy.drainAllGp(storages);
        StoragePolicy.drainAllFood(storages);
        int recovered = (int) Math.floor(totalGp * StoragePolicy.TERRITORY_LOSS_TRANSFER_RATE);
        if (recovered > 0) {
            findOrCreateVault(event.formerOwnerId()).receiveGp(recovered);
        }
        log.info(
                "영토 상실 저장 GP 환수. territoryId={}, formerOwnerId={}, recoveredGp={}",
                event.territoryId(),
                event.formerOwnerId(),
                recovered);
    }

    private List<BuildingInstance> findTerritoryStorages(Long territoryId) {
        List<BuildingInstance> storages =
                buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(territoryId);
        if (storages.isEmpty()) {
            throw new CustomException(ErrorCode.STORAGE_NOT_FOUND);
        }
        return storages;
    }

    private VaultTransferResponse transferToVault(
            GlobalVault vault, List<BuildingInstance> storages, VaultTransferRequest request) {
        int amount = request.amount().intValue();
        if (StoragePolicy.totalGp(storages) < amount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_GP);
        }
        if (vault.getStoredGp() + amount > vault.getCapacity()) {
            throw new CustomException(ErrorCode.VAULT_CAPACITY_EXCEEDED);
        }
        StoragePolicy.drainGp(storages, amount);
        vault.receiveGp(amount);
        vault.recordTransfer();
        return buildResponse(request, storages, vault);
    }

    private VaultTransferResponse transferFromVault(
            GlobalVault vault, List<BuildingInstance> storages, VaultTransferRequest request) {
        int amount = request.amount().intValue();
        if (vault.getStoredGp() < amount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_GP);
        }
        if (StoragePolicy.roomGp(storages) < amount) {
            throw new CustomException(ErrorCode.STORAGE_CAPACITY_EXCEEDED);
        }
        StoragePolicy.fillGp(storages, amount);
        vault.withdrawGp(amount);
        vault.recordTransfer();
        return buildResponse(request, storages, vault);
    }

    private VaultTransferResponse buildResponse(
            VaultTransferRequest request, List<BuildingInstance> storages, GlobalVault vault) {
        return new VaultTransferResponse(
                request.direction(),
                request.amount(),
                request.sourceTerritoryId(),
                StoragePolicy.totalGp(storages),
                vault.getStoredGp(),
                vault.getCapacity(),
                nextTransferAvailableAt(vault));
    }

    private GlobalVault findOrCreateVault(Long userId) {
        return globalVaultRepository.findByIdWithLock(userId).orElseGet(() -> createVault(userId));
    }

    private GlobalVault createVault(Long userId) {
        if (!userSnapshotRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return globalVaultRepository.save(GlobalVault.builder().userId(userId).build());
    }

    private TerritoryContext findTerritory(Long territoryId) {
        return territoryContextPort
                .findById(territoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
    }

    private void validateTerritoryOwner(TerritoryContext territory, Long userId) {
        if (!userId.equals(territory.ownerId())) {
            throw new CustomException(ErrorCode.NOT_TERRITORY_OWNER);
        }
    }

    private void validateCooldown(GlobalVault vault) {
        LocalDateTime next = nextTransferAvailableAt(vault);
        if (next != null && LocalDateTime.now().isBefore(next)) {
            throw new CustomException(ErrorCode.TRANSFER_COOLDOWN_ACTIVE);
        }
    }

    private LocalDateTime nextTransferAvailableAt(GlobalVault vault) {
        return vault.getLastTransferAt() == null
                ? null
                : vault.getLastTransferAt().plusMinutes(TRANSFER_COOLDOWN_MINUTES);
    }
}
