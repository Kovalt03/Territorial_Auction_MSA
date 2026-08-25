package com.territorial.auction.domain.item.dto;

import com.territorial.auction.domain.item.entity.Item;
import java.util.List;

public record ItemListResponse(List<ItemInfo> items) {

    public record ItemInfo(
            Long itemId,
            String name,
            String itemType,
            String description,
            Integer costAP,
            Integer costGP,
            Integer dailyLimit,
            int myInventory,
            String iconUrl) {

        public static ItemInfo of(Item item, int myInventory) {
            return new ItemInfo(
                    item.getId(),
                    item.getName(),
                    item.getItemType().name(),
                    item.getDescription(),
                    item.getCostAp(),
                    item.getCostGp(),
                    item.getDailyLimit(),
                    myInventory,
                    item.getIconUrl());
        }
    }
}
