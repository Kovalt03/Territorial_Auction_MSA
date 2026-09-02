package com.territorial.combat.domain.military.repository;

import com.territorial.combat.domain.military.entity.SiegeResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiegeResultRepository extends JpaRepository<SiegeResult, Long> {
    Optional<SiegeResult> findBySiegeId(Long siegeId);
}
