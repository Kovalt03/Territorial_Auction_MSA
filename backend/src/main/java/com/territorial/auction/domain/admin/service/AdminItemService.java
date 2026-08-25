package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminGrantItemRequest;
import com.territorial.auction.domain.admin.dto.AdminItemListResponse;
import com.territorial.auction.domain.admin.dto.AdminItemResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateItemRequest;
import com.territorial.auction.domain.item.entity.Item;
import com.territorial.auction.domain.item.entity.UserItem;
import com.territorial.auction.domain.item.repository.ItemRepository;
import com.territorial.auction.domain.item.repository.UserItemRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminItemService {

    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogger adminAuditLogger;

    public AdminItemListResponse getItems() {
        List<AdminItemResponse> items =
                itemRepository.findAll().stream().map(AdminItemResponse::from).toList();
        return new AdminItemListResponse(items);
    }

    @Transactional
    public AdminItemResponse updateItem(
            Long adminUserId, Long itemId, AdminUpdateItemRequest request) {
        Item item =
                itemRepository
                        .findById(itemId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

        Map<String, Object> before = new HashMap<>();
        before.put("costAp", item.getCostAp());
        before.put("costGp", item.getCostGp());
        before.put("dailyLimit", item.getDailyLimit());

        item.updatePolicy(request.costAp(), request.costGp(), request.dailyLimit());

        Map<String, Object> detail = new HashMap<>();
        detail.put("before", before);
        detail.put("costAp", request.costAp());
        detail.put("costGp", request.costGp());
        detail.put("dailyLimit", request.dailyLimit());
        adminAuditLogger.record(adminUserId, "ITEM_POLICY_UPDATE", "ITEM", itemId, detail);
        return AdminItemResponse.from(item);
    }

    // CS 보상용 아이템 직접 지급. 기존 보유분에 수량을 더한다(없으면 신규 생성).
    @Transactional
    public void grantItem(Long adminUserId, AdminGrantItemRequest request) {
        User user =
                userRepository
                        .findById(request.userId())
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Item item =
                itemRepository
                        .findById(request.itemId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

        userItemRepository
                .findByUser_IdAndItem_Id(user.getId(), item.getId())
                .ifPresentOrElse(
                        existing -> existing.add(request.quantity()),
                        () ->
                                userItemRepository.save(
                                        UserItem.builder()
                                                .user(user)
                                                .item(item)
                                                .quantity(request.quantity())
                                                .createdAt(LocalDateTime.now())
                                                .build()));

        adminAuditLogger.record(
                adminUserId,
                "ITEM_GRANT",
                "USER",
                user.getId(),
                Map.of(
                        "itemId", item.getId(),
                        "itemName", item.getName(),
                        "quantity", request.quantity(),
                        "reason", request.reason()));
    }
}
