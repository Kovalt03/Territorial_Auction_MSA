package com.territorial.notification.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCombatEventRepository
        extends JpaRepository<ProcessedCombatEvent, String> {}
