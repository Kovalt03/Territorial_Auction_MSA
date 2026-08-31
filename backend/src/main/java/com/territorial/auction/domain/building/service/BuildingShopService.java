package com.territorial.auction.domain.building.service;

import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse;
import com.territorial.auction.domain.building.dto.PurchaseDecorationResponse;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.client.WalletSnapshot;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
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
    private final WalletClient walletClient;

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

        // 로컬 저장 먼저 → AP 소비를 마지막에. spend 실패(잔액부족 등)면 이 트랜잭션이 롤백돼 건물 저장도 취소된다(정합).
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

        WalletSnapshot wallet =
                walletClient.spend(
                        userId, type.getApCost(), "BUILDING_SHOP:" + userId + ":" + stored.getId());

        log.info("장식 구매. userId={}, type={}, apCost={}", userId, type.getName(), type.getApCost());
        return new PurchaseDecorationResponse(
                stored.getId(), type.getName(), type.getDisplayName(), wallet.availableAp());
    }
}
