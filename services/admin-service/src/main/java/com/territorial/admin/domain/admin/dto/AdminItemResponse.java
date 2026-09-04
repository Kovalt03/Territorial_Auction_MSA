package com.territorial.admin.domain.admin.dto;

import com.territorial.admin.client.ItemAdminClient.ItemView;

public record AdminItemResponse(
        Long itemId,
        String name,
        String itemType,
        String description,
        Integer costAp,
        Integer costGp,
        Integer dailyLimit,
        Integer gpReward,
        String iconUrl) {

    public static AdminItemResponse from(ItemView item) {
        return new AdminItemResponse(
                item.itemId(),
                item.name(),
                item.itemType(),
                item.description(),
                item.costAp(),
                item.costGp(),
                item.dailyLimit(),
                item.gpReward(),
                item.iconUrl());
    }
}
