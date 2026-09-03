package com.territorial.item.internal.dto;

import com.territorial.item.domain.item.entity.Item;

/** item-service 내부 계약(season·admin 위임) 요청/응답 모음. */
public final class ItemInternalDtos {

    private ItemInternalDtos() {}

    // season 시즌패스 보상·admin 지급 등 아이템 타입 기준 지급
    public record GrantByTypeRequest(Long userId, String itemType, int quantity) {}

    // admin CS 보상 등 아이템 ID 기준 지급
    public record GrantByIdRequest(Long userId, Long itemId, int quantity) {}

    public record GrantResult(Long itemId, String itemName, int totalOwned) {}

    public record UpdatePolicyRequest(Integer costAp, Integer costGp, Integer dailyLimit) {}

    public record ItemAdminView(
            Long itemId,
            String name,
            String itemType,
            String description,
            Integer costAp,
            Integer costGp,
            Integer dailyLimit,
            Integer gpReward,
            String iconUrl) {
        public static ItemAdminView from(Item item) {
            return new ItemAdminView(
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
}
