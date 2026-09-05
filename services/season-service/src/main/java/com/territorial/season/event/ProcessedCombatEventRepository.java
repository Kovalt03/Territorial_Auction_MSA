package com.territorial.season.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCombatEventRepository
        extends JpaRepository<ProcessedCombatEvent, String> {}
