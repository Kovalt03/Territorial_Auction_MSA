package com.territorial.combat.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CombatOutboxEventRepository extends JpaRepository<CombatOutboxEvent, String> {
    List<CombatOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
