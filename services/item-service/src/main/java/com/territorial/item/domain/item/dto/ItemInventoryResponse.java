package com.territorial.item.domain.item.dto;

import com.territorial.item.domain.item.entity.UserItem;
import java.time.LocalDateTime;
import java.util.List;

public record ItemInventoryResponse(long totalCount, List<UserItemInfo> items) {

    public record UserItemInfo(
            Long userItemId,
            Long itemId,
            String itemName,
            String itemType,
            String description,
            int quantity,
            LocalDateTime acquiredAt) {

        public static UserItemInfo from(UserItem ui) {
            return new UserItemInfo(
                    ui.getId(),
                    ui.getItem().getId(),
                    ui.getItem().getName(),
                    ui.getItem().getItemType().name(),
                    ui.getItem().getDescription(),
                    ui.getQuantity(),
                    ui.getCreatedAt());
        }
    }
}
