package com.territorial.map.domain.map.repository;

import com.territorial.map.domain.map.entity.ColorHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColorHistoryRepository extends JpaRepository<ColorHistory, Long> {

    long countByTerritoryIdAndUserId(Long territoryId, Long userId);
}
