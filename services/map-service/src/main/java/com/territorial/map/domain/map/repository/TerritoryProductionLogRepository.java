package com.territorial.map.domain.map.repository;

import com.territorial.map.domain.map.entity.TerritoryProductionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerritoryProductionLogRepository
        extends JpaRepository<TerritoryProductionLog, Long> {}
