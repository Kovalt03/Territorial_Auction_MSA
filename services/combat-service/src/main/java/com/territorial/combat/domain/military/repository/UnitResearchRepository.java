package com.territorial.combat.domain.military.repository;

import com.territorial.combat.domain.military.entity.UnitResearch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitResearchRepository extends JpaRepository<UnitResearch, Long> {
    List<UnitResearch> findByUserId(Long userId);

    Optional<UnitResearch> findByUserIdAndUnitTypeId(Long userId, Long unitTypeId);
}
