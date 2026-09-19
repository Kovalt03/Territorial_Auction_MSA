package com.territorial.item.domain.item.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.item.client.CombatResourceClient;
import com.territorial.item.client.TerritoryOwnershipClient;
import com.territorial.item.client.WalletClient;
import com.territorial.item.domain.item.dto.ItemInventoryResponse;
import com.territorial.item.domain.item.dto.ItemInventoryResponse.UserItemInfo;
import com.territorial.item.domain.item.dto.ItemListResponse;
import com.territorial.item.domain.item.dto.ItemListResponse.ItemInfo;
import com.territorial.item.domain.item.dto.PurchaseItemRequest;
import com.territorial.item.domain.item.dto.PurchaseItemResponse;
import com.territorial.item.domain.item.dto.UseItemRequest;
import com.territorial.item.domain.item.dto.UseItemResponse;
import com.territorial.item.domain.item.dto.UseItemResponse.UseResult;
import com.territorial.item.domain.item.entity.Item;
import com.territorial.item.domain.item.entity.Item.ItemType;
import com.territorial.item.domain.item.entity.ItemPurchase;
import com.territorial.item.domain.item.entity.UserItem;
import com.territorial.item.domain.item.repository.ItemPurchaseRepository;
import com.territorial.item.domain.item.repository.ItemRepository;
import com.territorial.item.domain.item.repository.UserItemRepository;
import com.territorial.item.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final WalletClient walletClient;
    private final CombatResourceClient combatResourceClient;
    private final TerritoryOwnershipClient territoryOwnershipClient;
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

        // 한도 있는 아이템은 검사→구매를 유저 단위로 직렬화해 동시 구매의 한도 우회를 막는다.
        if (item.getDailyLimit() != null) {
            itemPurchaseRepository.acquireDailyLimitLock(userId);
        }
        validateDailyLimit(userId, item, request.quantity());

        int totalCost = item.getCostAp() * request.quantity();
        // 클라 멱등 키가 있으면 재시도에도 동일 commandKey → 지갑이 이중 차감을 dedup. 없으면 랜덤(하위 호환).
        String idempotencyKey =
                request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                        ? request.idempotencyKey()
                        : UUID.randomUUID().toString();
        String commandKey = "ITEM:" + idempotencyKey;

        int totalOwned = 0;
        WalletClient.WalletSnapshot wallet;
        if (item.getItemType() == ItemType.GP_PURCHASE) {
            // GP 지급은 combat 원격 커밋이라 로컬 롤백으로 못 되돌린다. 돈(spend)을 먼저 차감하고 지급하며,
            // 지급 실패 시 환불(credit)로 보상한다 — spend 실패 시엔 지급 자체가 없어 정합(무료 GP 방지).
            wallet = walletClient.spend(userId, totalCost, commandKey);
            int gpReward = item.getGpReward() != null ? item.getGpReward() : 0;
            try {
                creditVault(userId, gpReward * request.quantity(), commandKey + ":GP");
            } catch (RuntimeException e) {
                try {
                    walletClient.refund(userId, totalCost, commandKey + ":REFUND");
                } catch (RuntimeException refundEx) {
                    log.error(
                            "[ItemService] GP 지급 실패 후 환불도 실패 — 수동 확인 필요."
                                    + " userId={} cost={} commandKey={}",
                            userId,
                            totalCost,
                            commandKey,
                            refundEx);
                }
                throw e;
            }
            saveItemPurchaseLog(userId, item, request.quantity());
            invalidateItemCache(userId);
            return new PurchaseItemResponse(
                    item.getId(),
                    item.getItemType().name(),
                    request.quantity(),
                    totalOwned,
                    totalCost,
                    wallet.availableAp());
        }

        // 로컬 지급(일반 아이템) — spend를 마지막에 두어, 실패 시 트랜잭션 롤백으로 지급도 취소(정합)
        totalOwned = upsertUserItem(userId, item, request.quantity());
        saveItemPurchaseLog(userId, item, request.quantity());
        invalidateItemCache(userId);
        wallet = walletClient.spend(userId, totalCost, commandKey);

        return new PurchaseItemResponse(
                item.getId(),
                item.getItemType().name(),
                request.quantity(),
                totalOwned,
                totalCost,
                wallet.availableAp());
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
                        .findByUserIdAndItem_Id(userId, item.getId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

        if (userItem.getQuantity() <= 0) {
            throw new CustomException(ErrorCode.ITEM_OUT_OF_STOCK);
        }

        UseResult result =
                switch (item.getItemType()) {
                    case INVINCIBILITY -> applyInvincibility(userId, request.targetTerritoryId());
                    case ATTACK_NORMAL ->
                            applyAttackToken(
                                    userId,
                                    false,
                                    "ITEM_USE:"
                                            + userId
                                            + ":"
                                            + userItem.getId()
                                            + ":"
                                            + userItem.getQuantity());
                    case ATTACK_PRECISION ->
                            applyAttackToken(
                                    userId,
                                    true,
                                    "ITEM_USE:"
                                            + userId
                                            + ":"
                                            + userItem.getId()
                                            + ":"
                                            + userItem.getQuantity());
                    default -> throw new CustomException(ErrorCode.ITEM_NOT_USABLE);
                };
        userItem.use();
        invalidateItemCache(userId);

        return new UseItemResponse(
                item.getId(), item.getItemType().name(), result, userItem.getQuantity());
    }

    public ItemInventoryResponse getInventory(Long userId, Pageable pageable) {
        Page<UserItem> page = userItemRepository.findByUserId(userId, pageable);
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

    // 보상 GP는 위치가 없으므로 combat-service 금고로 적립한다.
    private void creditVault(Long userId, int amount, String commandKey) {
        if (amount <= 0) return;
        combatResourceClient.creditGp(userId, amount, commandKey);
    }

    private int upsertUserItem(Long userId, Item item, int quantity) {
        return userItemRepository
                .findByUserIdAndItem_Id(userId, item.getId())
                .map(
                        existing -> {
                            existing.add(quantity);
                            return existing.getQuantity();
                        })
                .orElseGet(
                        () ->
                                userItemRepository
                                        .save(
                                                UserItem.builder()
                                                        .userId(userId)
                                                        .item(item)
                                                        .quantity(quantity)
                                                        .createdAt(LocalDateTime.now())
                                                        .build())
                                        .getQuantity());
    }

    private void saveItemPurchaseLog(Long userId, Item item, int quantity) {
        itemPurchaseRepository.save(
                ItemPurchase.builder()
                        .userId(userId)
                        .item(item)
                        .quantity(quantity)
                        .purchasedAt(LocalDateTime.now())
                        .build());
    }

    private UseResult applyInvincibility(Long userId, Long targetTerritoryId) {
        if (targetTerritoryId == null) {
            throw new CustomException(ErrorCode.TARGET_TERRITORY_REQUIRED);
        }

        Long ownerId = territoryOwnershipClient.getOwnerId(targetTerritoryId);
        if (ownerId == null || !ownerId.equals(userId)) {
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

    private UseResult applyAttackToken(Long userId, boolean isPrecision, String commandKey) {
        var token =
                combatResourceClient.creditAttackTokens(
                        userId, isPrecision ? 0 : 1, isPrecision ? 1 : 0, commandKey);
        log.info(
                "공격권 지급. userId={}, type={}, normalCount={}, precisionCount={}",
                userId,
                isPrecision ? "PRECISION" : "NORMAL",
                token.normalCount(),
                token.precisionCount());
        return UseResult.ofAttackToken(token.normalCount(), token.precisionCount());
    }

    private Map<Long, Integer> buildInventoryMap(Long userId) {
        return userItemRepository.findAllByUserId(userId).stream()
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
