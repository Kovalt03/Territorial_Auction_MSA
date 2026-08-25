package com.territorial.auction.domain.building.dto;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import java.time.LocalDateTime;
import java.util.List;

public record TerritoryBuildingResponse(List<BuildingInfo> buildings) {

    public record BuildingInfo(
            Long buildingId,
            String type,
            String name,
            Integer posX,
            Integer posY,
            Integer width,
            Integer height,
            Integer hp,
            Integer maxHp,
            Integer level,
            Integer zone,
            boolean isDestroyed,
            LocalDateTime buildCompleteAt) {

        public static BuildingInfo from(BuildingInstance bi) {
            return new BuildingInfo(
                    bi.getId(),
                    bi.getBuildingType().getName(),
                    bi.getBuildingType().getName(),
                    bi.getPosX(),
                    bi.getPosY(),
                    bi.getBuildingType().getWidth(),
                    bi.getBuildingType().getHeight(),
                    bi.getHp(),
                    bi.getBuildingType().getMaxHp(),
                    bi.getLevel(),
                    bi.getZone(),
                    bi.isDestroyed(),
                    bi.getBuildCompleteAt());
        }
    }
}
