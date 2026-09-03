package com.territorial.combat.domain.building.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.dto.BuildingTypeCatalogResponse;
import com.territorial.combat.domain.building.dto.PurchaseDecorationResponse;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.port.WalletPort;
import com.territorial.combat.domain.building.port.WalletPort.WalletSnapshot;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingTypeRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnBean(WalletPort.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingShopService {

    private static final int INVENTORY_POSITION = -1;

    private final BuildingTypeRepository buildingTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final CombatUserSnapshotRepository userSnapshotRepository;
    private final WalletPort walletPort;

    public BuildingTypeCatalogResponse getShop() {
        return BuildingTypeCatalogResponse.of(
                buildingTypeRepository.findAll().stream()
                        .filter(BuildingType::isPurchasable)
                        .toList());
    }

    @Transactional
    public PurchaseDecorationResponse purchase(Long userId, Long buildingTypeId) {
        BuildingType type = findPurchasableType(buildingTypeId);
        validateUserExists(userId);
        BuildingInstance stored = createInventoryBuilding(userId, type);
        WalletSnapshot wallet =
                walletPort.spend(
                        userId, type.getApCost(), "BUILDING_SHOP:" + userId + ":" + stored.getId());
        log.info("장식 구매. userId={}, type={}, apCost={}", userId, type.getName(), type.getApCost());
        return new PurchaseDecorationResponse(
                stored.getId(), type.getName(), type.getDisplayName(), wallet.availableAp());
    }

    private BuildingType findPurchasableType(Long buildingTypeId) {
        BuildingType type =
                buildingTypeRepository
                        .findById(buildingTypeId)
                        .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
        if (!type.isPurchasable()) {
            throw new CustomException(ErrorCode.BUILDING_NOT_PURCHASABLE);
        }
        return type;
    }

    private void validateUserExists(Long userId) {
        if (!userSnapshotRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private BuildingInstance createInventoryBuilding(Long userId, BuildingType type) {
        return buildingInstanceRepository.save(
                BuildingInstance.builder()
                        .ownerId(userId)
                        .buildingType(type)
                        .posX(INVENTORY_POSITION)
                        .posY(INVENTORY_POSITION)
                        .hp(type.getMaxHp())
                        .zone(0)
                        .build());
    }
}
