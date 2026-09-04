package com.territorial.map.domain.map.repository;

import com.territorial.map.domain.map.entity.BonusTile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BonusTileRepository extends JpaRepository<BonusTile, Long> {

    Optional<BonusTile> findByTerritoryId(Long territoryId);
}
