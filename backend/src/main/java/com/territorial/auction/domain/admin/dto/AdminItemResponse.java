package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.item.entity.Item;

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

    public static AdminItemResponse from(Item item) {
        return new AdminItemResponse(
                item.getId(),
                item.getName(),
                item.getItemType().name(),
                item.getDescription(),
                item.getCostAp(),
                item.getCostGp(),
                item.getDailyLimit(),
                item.getGpReward(),
                item.getIconUrl());
    }
}
