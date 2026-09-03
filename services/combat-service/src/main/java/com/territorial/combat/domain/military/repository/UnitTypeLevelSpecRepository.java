package com.territorial.combat.domain.military.repository;

import com.territorial.combat.domain.military.entity.UnitTypeLevelSpec;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitTypeLevelSpecRepository extends JpaRepository<UnitTypeLevelSpec, Long> {
    List<UnitTypeLevelSpec> findAllByUnitType_Id(Long unitTypeId);

    Optional<UnitTypeLevelSpec> findByUnitType_IdAndLevel(Long unitTypeId, int level);
}
