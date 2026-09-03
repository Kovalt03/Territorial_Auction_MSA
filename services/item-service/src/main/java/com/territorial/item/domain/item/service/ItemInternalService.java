package com.territorial.item.domain.item.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.item.domain.item.entity.Item;
import com.territorial.item.domain.item.entity.UserItem;
import com.territorial.item.domain.item.repository.ItemRepository;
import com.territorial.item.domain.item.repository.UserItemRepository;
import com.territorial.item.global.exception.ErrorCode;
import com.territorial.item.internal.dto.ItemInternalDtos.GrantResult;
import com.territorial.item.internal.dto.ItemInternalDtos.ItemAdminView;
import com.territorial.item.internal.dto.ItemInternalDtos.UpdatePolicyRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** season 보상·admin 위임 등 서비스 간 내부 호출을 처리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemInternalService {

    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;

    @Transactional
    public GrantResult grantByType(Long userId, Item.ItemType itemType, int quantity) {
        Item item =
                itemRepository
                        .findByItemType(itemType)
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
        return grant(userId, item, quantity);
    }

    @Transactional
    public GrantResult grantById(Long userId, Long itemId, int quantity) {
        Item item =
                itemRepository
                        .findById(itemId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
        return grant(userId, item, quantity);
    }

    public List<ItemAdminView> listItems() {
        return itemRepository.findAll().stream().map(ItemAdminView::from).toList();
    }

    @Transactional
    public ItemAdminView updatePolicy(Long itemId, UpdatePolicyRequest request) {
        Item item =
                itemRepository
                        .findById(itemId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
        item.updatePolicy(request.costAp(), request.costGp(), request.dailyLimit());
        return ItemAdminView.from(item);
    }

    private GrantResult grant(Long userId, Item item, int quantity) {
        int totalOwned =
                userItemRepository
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
        return new GrantResult(item.getId(), item.getName(), totalOwned);
    }
}
