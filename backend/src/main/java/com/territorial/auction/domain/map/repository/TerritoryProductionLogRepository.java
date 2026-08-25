package com.territorial.auction.domain.map.repository;

import com.territorial.auction.domain.map.entity.TerritoryProductionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerritoryProductionLogRepository
        extends JpaRepository<TerritoryProductionLog, Long> {}
