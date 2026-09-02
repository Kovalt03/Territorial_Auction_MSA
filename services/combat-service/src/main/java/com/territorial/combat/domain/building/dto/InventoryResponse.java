package com.territorial.combat.domain.building.dto;

import com.territorial.combat.domain.building.entity.BuildingInstance;
import java.time.LocalDateTime;
import java.util.List;

public record InventoryResponse(List<InventoryItem> items) {

    public record InventoryItem(
            Long inventoryId,
            Long buildingTypeId,
            String buildingTypeName,
            String buildingType,
            int quantity,
            LocalDateTime acquiredAt) {

        public static InventoryItem from(BuildingInstance bi) {
            return new InventoryItem(
                    bi.getId(),
                    bi.getBuildingType().getId(),
                    bi.getBuildingType().getName(),
                    bi.getBuildingType().getName(),
                    1,
                    LocalDateTime.now());
        }
    }
}
