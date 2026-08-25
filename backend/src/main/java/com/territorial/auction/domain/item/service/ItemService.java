package com.territorial.auction.domain.item.service;

import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.item.dto.ItemInventoryResponse;
import com.territorial.auction.domain.item.dto.ItemInventoryResponse.UserItemInfo;
import com.territorial.auction.domain.item.dto.ItemListResponse;
import com.territorial.auction.domain.item.dto.ItemListResponse.ItemInfo;
import com.territorial.auction.domain.item.dto.PurchaseItemRequest;
import com.territorial.auction.domain.item.dto.PurchaseItemResponse;
import com.territorial.auction.domain.item.dto.UseItemRequest;
import com.territorial.auction.domain.item.dto.UseItemResponse;
import com.territorial.auction.domain.item.dto.UseItemResponse.UseResult;
import com.territorial.auction.domain.item.entity.Item;
import com.territorial.auction.domain.item.entity.Item.ItemType;
import com.territorial.auction.domain.item.entity.ItemPurchase;
import com.territorial.auction.domain.item.entity.UserItem;
import com.territorial.auction.domain.item.repository.ItemPurchaseRepository;
import com.territorial.auction.domain.item.repository.ItemRepository;
import com.territorial.auction.domain.item.repository.UserItemRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.military.entity.AttackToken;
import com.territorial.auction.domain.military.repository.AttackTokenRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private static final String CACHE_USER_ITEMS = "user:item:";
    private static final String INVINCIBLE_KEY = "invincible:";
    private static final Duration INVINCIBLE_TTL = Duration.ofHours(1);

    private final ItemRepository itemRepository;
    private final ItemPurchaseRepository itemPurchaseRepository;
    private final UserItemRepository userItemRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final TerritoryRepository territoryRepository;
    private final AttackTokenRepository attackTokenRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public ItemListResponse getItems(Long userId) {
        List<Item> items = itemRepository.findAll();
        Map<Long, Integer> inventoryMap = buildInventoryMap(userId);

        List<ItemInfo> itemInfos =
                items.stream()
                        .map(item -> ItemInfo.of(item, inventoryMap.getOrDefault(item.getId(), 0)))
                        .toList();

        return new ItemListResponse(itemInfos);
    }

    @Transactional
    public PurchaseItemResponse purchaseItem(Long userId, PurchaseItemRequest request) {
        Item item =
                itemRepository
                        .findById(request.itemId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

        validateDailyLimit(userId, item, request.quantity());

        Wallet wallet =
                walletRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        int totalCost = item.getCostAp() * request.quantity();
        if (wallet.getAvailableAp() < totalCost) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }

        wallet.spendAp(totalCost);

        int totalOwned = 0;
        if (item.getItemType() == ItemType.GP_PURCHASE) {
            int gpReward = item.getGpReward() != null ? item.getGpReward() : 0;
            creditVault(userId, gpReward * request.quantity());
        } else {
            totalOwned = upsertUserItem(userId, item, request.quantity());
        }

        saveItemPurchaseLog(userId, item, request.quantity());
        invalidateItemCache(userId);

        return new PurchaseItemResponse(
                item.getId(),
                item.getItemType().name(),
                request.quantity(),
                totalOwned,
                totalCost,
                wallet.getAvailableAp());
    }

    @Transactional
    public UseItemResponse useItem(Long userId, UseItemRequest request) {
        Item item =
                itemRepository
                        .findById(request.itemId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

        if (item.getItemType() == ItemType.GP_PURCHASE) {
            throw new CustomException(ErrorCode.ITEM_NOT_USABLE);
        }

        UserItem userItem =
                userItemRepository
                        .findByUser_IdAndItem_Id(userId, item.getId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

        if (userItem.getQuantity() <= 0) {
            throw new CustomException(ErrorCode.ITEM_OUT_OF_STOCK);
        }

        UseResult result =
                switch (item.getItemType()) {
                    case INVINCIBILITY -> applyInvincibility(userId, request.targetTerritoryId());
                    case ATTACK_NORMAL -> applyAttackToken(userId, false);
                    case ATTACK_PRECISION -> applyAttackToken(userId, true);
                    default -> throw new CustomException(ErrorCode.ITEM_NOT_USABLE);
                };
        userItem.use();
        invalidateItemCache(userId);

        return new UseItemResponse(
                item.getId(), item.getItemType().name(), result, userItem.getQuantity());
    }

    public ItemInventoryResponse getInventory(Long userId, Pageable pageable) {
        Page<UserItem> page = userItemRepository.findByUser_Id(userId, pageable);
        List<UserItemInfo> items = page.getContent().stream().map(UserItemInfo::from).toList();
        return new ItemInventoryResponse(page.getTotalElements(), items);
    }

    private void validateDailyLimit(Long userId, Item item, int requestedQuantity) {
        if (item.getDailyLimit() == null) {
            return;
        }
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int todayCount = itemPurchaseRepository.sumTodayQuantity(userId, item.getId(), startOfDay);
        if (todayCount + requestedQuantity > item.getDailyLimit()) {
            throw new CustomException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }
    }

    // 보상 GP는 위치가 없으므로 금고로 적립한다. 금고가 없으면 만든다.
    private void creditVault(Long userId, int amount) {
        if (amount <= 0) return;
        globalVaultRepository
                .findByIdWithLock(userId)
                .orElseGet(
                        () ->
                                globalVaultRepository.save(
                                        GlobalVault.builder()
                                                .user(userRepository.getReferenceById(userId))
                                                .build()))
                .receiveGp(amount);
    }

    private int upsertUserItem(Long userId, Item item, int quantity) {
        return userItemRepository
                .findByUser_IdAndItem_Id(userId, item.getId())
                .map(
                        existing -> {
                            existing.add(quantity);
                            return existing.getQuantity();
                        })
                .orElseGet(
                        () -> {
                            User user =
                                    userRepository
                                            .findById(userId)
                                            .orElseThrow(
                                                    () ->
                                                            new CustomException(
                                                                    ErrorCode.USER_NOT_FOUND));
                            return userItemRepository
                                    .save(
                                            UserItem.builder()
                                                    .user(user)
                                                    .item(item)
                                                    .quantity(quantity)
                                                    .createdAt(LocalDateTime.now())
                                                    .build())
                                    .getQuantity();
                        });
    }

    private void saveItemPurchaseLog(Long userId, Item item, int quantity) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        itemPurchaseRepository.save(
                ItemPurchase.builder()
                        .user(user)
                        .item(item)
                        .quantity(quantity)
                        .purchasedAt(LocalDateTime.now())
                        .build());
    }

    private UseResult applyInvincibility(Long userId, Long targetTerritoryId) {
        if (targetTerritoryId == null) {
            throw new CustomException(ErrorCode.TARGET_TERRITORY_REQUIRED);
        }

        Territory territory =
                territoryRepository
                        .findById(targetTerritoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));

        if (territory.getOwner() == null || !territory.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_TERRITORY_OWNER);
        }

        String redisKey = INVINCIBLE_KEY + targetTerritoryId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new CustomException(ErrorCode.ALREADY_INVINCIBLE);
        }

        redisTemplate.opsForValue().set(redisKey, true, INVINCIBLE_TTL);
        LocalDateTime invincibleUntil = LocalDateTime.now().plus(INVINCIBLE_TTL);

        return UseResult.ofInvincibility(targetTerritoryId, invincibleUntil);
    }

    private UseResult applyAttackToken(Long userId, boolean isPrecision) {
        AttackToken token =
                attackTokenRepository
                        .findByUserIdWithLock(userId)
                        .orElseGet(
                                () -> {
                                    User user =
                                            userRepository
                                                    .findById(userId)
                                                    .orElseThrow(
                                                            () ->
                                                                    new CustomException(
                                                                            ErrorCode
                                                                                    .USER_NOT_FOUND));
                                    return attackTokenRepository.save(
                                            AttackToken.builder().user(user).build());
                                });
        if (isPrecision) {
            token.addPrecision();
        } else {
            token.addNormal();
        }
        log.info(
                "공격권 지급. userId={}, type={}, normalCount={}, precisionCount={}",
                userId,
                isPrecision ? "PRECISION" : "NORMAL",
                token.getNormalCount(),
                token.getPrecisionCount());
        return UseResult.ofAttackToken(token.getNormalCount(), token.getPrecisionCount());
    }

    private Map<Long, Integer> buildInventoryMap(Long userId) {
        return userItemRepository.findAllByUser_Id(userId).stream()
                .collect(Collectors.toMap(ui -> ui.getItem().getId(), UserItem::getQuantity));
    }

    private void invalidateItemCache(Long userId) {
        try {
            redisTemplate.delete(CACHE_USER_ITEMS + userId);
        } catch (Exception e) {
            log.warn("아이템 Redis 캐시 무효화 실패. userId={}", userId);
        }
    }
}
