package com.territorial.auction.domain.building.service;

import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse;
import com.territorial.auction.domain.building.dto.PurchaseDecorationResponse;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 장식 블록 AP 상점: 구매 시 보관함(인벤토리)에 담기고, 사용자가 섬에 배치한다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingShopService {

    private final BuildingTypeRepository buildingTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    private static final int INVENTORY_POS = -1;

    /** 판매 중인 장식 건물 목록(AP 판매가 지정된 장식 건물) */
    public BuildingTypeCatalogResponse getShop() {
        return BuildingTypeCatalogResponse.of(
                buildingTypeRepository.findAll().stream()
                        .filter(BuildingType::isPurchasable)
                        .toList());
    }

    @Transactional
    public PurchaseDecorationResponse purchase(Long userId, Long buildingTypeId) {
        BuildingType type =
                buildingTypeRepository
                        .findById(buildingTypeId)
                        .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
        if (!type.isPurchasable()) {
            throw new CustomException(ErrorCode.BUILDING_NOT_PURCHASABLE);
        }
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Wallet wallet =
                walletRepository
                        .findByIdWithLock(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getAvailableAp() < type.getApCost()) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }
        wallet.spendAp(type.getApCost());

        BuildingInstance stored =
                buildingInstanceRepository.save(
                        BuildingInstance.builder()
                                .owner(user)
                                .buildingType(type)
                                .posX(INVENTORY_POS)
                                .posY(INVENTORY_POS)
                                .hp(type.getMaxHp())
                                .zone(0)
                                .build());

        log.info("장식 구매. userId={}, type={}, apCost={}", userId, type.getName(), type.getApCost());
        return new PurchaseDecorationResponse(
                stored.getId(), type.getName(), type.getDisplayName(), wallet.getAvailableAp());
    }
}
