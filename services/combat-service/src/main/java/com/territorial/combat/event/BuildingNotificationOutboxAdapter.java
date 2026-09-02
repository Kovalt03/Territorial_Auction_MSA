package com.territorial.combat.event;

import com.territorial.combat.domain.building.port.BuildingNotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BuildingNotificationOutboxAdapter implements BuildingNotificationPort {

    public static final String ISLAND_EXPANDED = "combat.island.expanded";

    private final CombatOutboxService outboxService;

    @Override
    public void notifyIslandExpanded(Long userId, int storedBuildingCount) {
        outboxService.append(
                "USER",
                userId,
                ISLAND_EXPANDED,
                new IslandExpandedEvent(userId, storedBuildingCount));
    }

    public record IslandExpandedEvent(Long userId, int storedBuildingCount) {}
}
