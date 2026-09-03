package com.territorial.combat.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuildingNotificationOutboxAdapterTest {

    @InjectMocks private BuildingNotificationOutboxAdapter adapter;
    @Mock private CombatOutboxService outboxService;

    @Test
    void islandExpansionIsStoredInCombatOutbox() {
        adapter.notifyIslandExpanded(1L, 3);

        then(outboxService)
                .should()
                .append(eq("USER"), eq(1L), eq("combat.island.expanded"), any());
    }
}
