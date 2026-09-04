package com.territorial.admin.domain.admin.service;

import com.territorial.admin.client.ItemAdminClient;
import com.territorial.admin.client.ItemAdminClient.GrantResult;
import com.territorial.admin.client.ItemAdminClient.ItemView;
import com.territorial.admin.client.UserAdminClient;
import com.territorial.admin.domain.admin.dto.AdminGrantItemRequest;
import com.territorial.admin.domain.admin.dto.AdminItemListResponse;
import com.territorial.admin.domain.admin.dto.AdminItemResponse;
import com.territorial.admin.domain.admin.dto.AdminUpdateItemRequest;
import com.territorial.admin.global.exception.ErrorCode;
import com.territorial.auction.global.exception.CustomException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 아이템 소유는 item-service. admin은 감사 로그만 모놀리식에 남기고 실제 아이템 조작은 위임한다. */
@Service
@RequiredArgsConstructor
public class AdminItemService {

    private final ItemAdminClient itemAdminClient;
    private final UserAdminClient userAdminClient;
    private final AdminAuditLogger adminAuditLogger;

    public AdminItemListResponse getItems() {
        List<AdminItemResponse> items =
                itemAdminClient.listItems().stream().map(AdminItemResponse::from).toList();
        return new AdminItemListResponse(items);
    }

    public AdminItemResponse updateItem(
            Long adminUserId, Long itemId, AdminUpdateItemRequest request) {
        ItemView current = findItemOrThrow(itemId);

        Map<String, Object> before = new HashMap<>();
        before.put("costAp", current.costAp());
        before.put("costGp", current.costGp());
        before.put("dailyLimit", current.dailyLimit());

        ItemView updated =
                itemAdminClient.updatePolicy(
                        itemId, request.costAp(), request.costGp(), request.dailyLimit());

        Map<String, Object> detail = new HashMap<>();
        detail.put("before", before);
        detail.put("costAp", request.costAp());
        detail.put("costGp", request.costGp());
        detail.put("dailyLimit", request.dailyLimit());
        adminAuditLogger.record(adminUserId, "ITEM_POLICY_UPDATE", "ITEM", itemId, detail);
        return AdminItemResponse.from(updated);
    }

    // CS 보상용 아이템 직접 지급. 지급은 item-service가 수행, 감사 로그는 모놀리식 유지.
    public void grantItem(Long adminUserId, AdminGrantItemRequest request) {
        if (!userAdminClient.exists(request.userId())) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        GrantResult result =
                itemAdminClient.grantById(request.userId(), request.itemId(), request.quantity());

        adminAuditLogger.record(
                adminUserId,
                "ITEM_GRANT",
                "USER",
                request.userId(),
                Map.of(
                        "itemId", result.itemId(),
                        "itemName", result.itemName(),
                        "quantity", request.quantity(),
                        "reason", request.reason()));
    }

    private ItemView findItemOrThrow(Long itemId) {
        return itemAdminClient.listItems().stream()
                .filter(i -> i.itemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    }
}
