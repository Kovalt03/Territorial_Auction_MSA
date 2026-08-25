package com.territorial.auction.domain.map.repository;

import com.territorial.auction.domain.map.entity.BonusTile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BonusTileRepository extends JpaRepository<BonusTile, Long> {

    Optional<BonusTile> findByTerritoryId(Long territoryId);
}
