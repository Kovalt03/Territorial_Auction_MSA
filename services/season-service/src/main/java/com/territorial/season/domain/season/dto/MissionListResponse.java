package com.territorial.season.domain.season.dto;

import java.util.List;

public record MissionListResponse(List<MissionItem> missions) {

    public record MissionItem(
            Long missionId,
            String code,
            String title,
            String description,
            String missionType,
            Integer goalCount,
            Integer completedCount,
            Integer xpReward,
            Boolean isClaimed,
            Boolean canClaim) {}
}
