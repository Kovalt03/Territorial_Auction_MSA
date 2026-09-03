package com.territorial.combat.domain.military.repository;

import com.territorial.combat.domain.military.entity.SiegeStructure;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiegeStructureRepository extends JpaRepository<SiegeStructure, Long> {
    List<SiegeStructure> findBySiegeId(Long siegeId);
}
